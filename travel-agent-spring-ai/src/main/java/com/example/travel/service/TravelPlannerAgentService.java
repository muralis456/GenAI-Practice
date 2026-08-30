package com.example.travel.service;

import com.example.travel.dto.TravelPlanResponse;
import com.example.travel.dto.TravelRequest;
import org.springframework.ai.chat.client.ChatClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Map;

@Service
public class TravelPlannerAgentService {

    private static final Logger log = LoggerFactory.getLogger(TravelPlannerAgentService.class);

    private final ChatClient chatClient;
    private final TravelAgentTools travelAgentTools;

    public TravelPlannerAgentService(ChatClient chatClient,
                                                TravelAgentTools travelAgentTools) {
        this.chatClient = chatClient;
        this.travelAgentTools = travelAgentTools;
    }

    public TravelPlanResponse createTravelPlan(TravelRequest request, String historyContext) {
        log.info("Starting travel plan orchestration for userId={}", request.getUserId());
        String promptText = request.getPrompt() != null ? request.getPrompt() : request.getPreferences();
        if (promptText == null || promptText.trim().isEmpty()) {
            promptText = "Plan a balanced family-friendly trip with good food and local experiences.";
        }

        String selectedModel = request.getSelectedModel() != null ? request.getSelectedModel() : "llama3.2:3b";
        String departureCity = request.getDepartureCity();
        LocalDate today = LocalDate.now();
        String departureDate = request.getDepartureDate() != null ? request.getDepartureDate() : today.toString();
        String returnDate = request.getReturnDate() != null ? request.getReturnDate() : today.plusDays(5).toString();
        String travelStyle = request.getTravelStyle() != null ? request.getTravelStyle() : "balanced";
        String budget = request.getBudget() != null ? request.getBudget() : "medium";

        request.setPreferences(promptText);
        request.setDepartureCity(departureCity);
        request.setDepartureDate(departureDate);
        request.setReturnDate(returnDate);
        request.setTravelStyle(travelStyle);
        request.setBudget(budget);

        travelAgentTools.startRequest();
        try {
            String finalPlan = chatClient.prompt()
                    .system("You are the main travel orchestrator. Use the registered travel tools when they are relevant to the user's request. "
                            + "Choose only the minimum required tools. Use searchHotels for hotel requests, searchFlights for flight requests, "
                            + "researchDestination for destination information, and buildItinerary for schedules or trip plans. "
                            + "After receiving tool results, produce the final answer for the user. Never use a destination or route from conversation history when it conflicts with the current request. For flight requests, preserve the exact FROM and TO direction and do not invent missing locations. Never output bracketed placeholders such as [Insert flight number]; use 'Unavailable' when data is missing.")
                    .user("CURRENT REQUEST: " + promptText + "\nDestination field (may be stale): " + request.getDestination()
                            + "\nDeparture city: " + departureCity + "\nDeparture date: " + departureDate
                            + "\nReturn date: " + returnDate + "\nBudget: " + budget + "\nTravel style: " + travelStyle
                            + "\nPrevious context (background only): " + historyContext)
                    .tools(travelAgentTools)
                    .call()
                    .content();

            Map<String, String> results = travelAgentTools.getResults();
            String destination = request.getDestination() != null ? request.getDestination() : "Destination from current request";
            return new TravelPlanResponse(request.getUserId(), destination, selectedModel, finalPlan,
                    results.getOrDefault("flightInsights", "Not requested for this trip."),
                    results.getOrDefault("travelInsights", "Not requested for this trip."),
                    results.getOrDefault("hotelInsights", "Not requested for this trip."),
                    results.getOrDefault("itinerary", "Not requested for this trip."));
        } finally {
            travelAgentTools.endRequest();
        }
    }
}

