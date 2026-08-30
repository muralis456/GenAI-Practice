package com.example.travel.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.travel.dto.TravelPlanResponse;
import com.example.travel.dto.TravelRequest;

@Service
public class TravelPlannerOrchestratorAgentService {

    private static final Logger log = LoggerFactory.getLogger(TravelPlannerOrchestratorAgentService.class);

    private final FlightAgentService flightAgentService;
    private final TravelResearchAgentService travelResearchAgentService;
    private final ItineraryAgentService itineraryAgentService;
    private final FinalPlannerAgentService finalPlannerAgentService;

    public TravelPlannerOrchestratorAgentService(FlightAgentService flightAgentService,
                                                TravelResearchAgentService travelResearchAgentService,
                                                ItineraryAgentService itineraryAgentService,
                                                FinalPlannerAgentService finalPlannerAgentService) {
        this.flightAgentService = flightAgentService;
        this.travelResearchAgentService = travelResearchAgentService;
        this.itineraryAgentService = itineraryAgentService;
        this.finalPlannerAgentService = finalPlannerAgentService;
    }

    public TravelPlanResponse createTravelPlan(TravelRequest request) {
        log.info("Starting travel plan orchestration for userId={}", request.getUserId());
        String promptText = request.getPrompt() != null ? request.getPrompt() : request.getPreferences();
        if (promptText == null || promptText.trim().isEmpty()) {
            promptText = "Plan a balanced family-friendly trip with good food and local experiences.";
        }

        String selectedModel = request.getSelectedModel() != null ? request.getSelectedModel() : "llama3.2:3b";
        String destination = request.getDestination() != null ? request.getDestination() : "Dubai";
        String departureCity = request.getDepartureCity() != null ? request.getDepartureCity() : "LHR";
        String departureDate = request.getDepartureDate() != null ? request.getDepartureDate() : "2026-09-15";
        String returnDate = request.getReturnDate() != null ? request.getReturnDate() : "2026-09-20";
        String travelStyle = request.getTravelStyle() != null ? request.getTravelStyle() : "balanced";
        String budget = request.getBudget() != null ? request.getBudget() : "medium";

        request.setPreferences(promptText);
        request.setDestination(destination);
        request.setDepartureCity(departureCity);
        request.setDepartureDate(departureDate);
        request.setReturnDate(returnDate);
        request.setTravelStyle(travelStyle);
        request.setBudget(budget);

        log.debug("Calling flight agent for departureCity={}, destination={}, date={}", departureCity, destination, departureDate);
        String flightInsights = flightAgentService.fetchFlightInsights(request, selectedModel);

        log.debug("Calling travel research agent for destination={}", destination);
        String travelInsights = travelResearchAgentService.fetchTravelResearch(request, selectedModel);

        log.debug("Building itinerary");
        String itinerary = itineraryAgentService.buildItinerary(request, selectedModel, flightInsights, travelInsights);

        log.debug("Building final plan");
        String finalPlan = finalPlannerAgentService.buildFinalPlan(request, selectedModel, flightInsights, travelInsights, itinerary);

        log.info("Travel plan orchestration completed for userId={}", request.getUserId());
        return new TravelPlanResponse(
                request.getUserId(),
                destination,
                selectedModel,
                finalPlan,
                flightInsights,
                travelInsights,
                itinerary
        );
    }
}

