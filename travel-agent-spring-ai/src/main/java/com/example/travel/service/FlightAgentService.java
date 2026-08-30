package com.example.travel.service;

import com.example.travel.dto.TravelRequest;
import com.example.travel.entity.AirportLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class FlightAgentService {

    private static final Logger log = LoggerFactory.getLogger(FlightAgentService.class);

    private final ChatClient chatClient;
    private final ExternalApiService externalApiService;
    private final AirportLookupService airportLookupService;

    public FlightAgentService(ChatClient chatClient, ExternalApiService externalApiService,
                              AirportLookupService airportLookupService) {
        this.chatClient = chatClient;
        this.externalApiService = externalApiService;
        this.airportLookupService = airportLookupService;
    }

    public String fetchFlightInsights(TravelRequest request, String selectedModel) {
        return fetchFlightInsights(request, selectedModel, null);
    }

    public String fetchFlightInsights(TravelRequest request, String selectedModel, String airportCode) {
        log.debug("Fetching flight insights for departureCity={}, destination={}, model={}", request.getDepartureCity(), request.getDestination(), selectedModel);
        String departureCityName = request.getDepartureCity();
        if (departureCityName != null && departureCityName.equalsIgnoreCase("LHR")) {
            log.warn("Ignoring LHR departure because the request did not explicitly provide London");
            departureCityName = null;
        }
        String departureCity = airportLookupService.findAirport(departureCityName)
            .map(AirportLocation::getIataCode)
            .orElse(departureCityName);
        String destination = request.getDestination();
        String destinationAirportCode = airportLookupService.findAirport(airportCode)
                .map(AirportLocation::getIataCode)
                .orElseGet(() -> airportLookupService.findAirport(destination)
                    .map(AirportLocation::getIataCode)
                    .orElse(null));
        String departureDate = request.getDepartureDate() != null ? request.getDepartureDate() : LocalDate.now().toString();

        if (destinationAirportCode == null) {
            log.warn("No airport found for destination={}; skipping flight lookup", destination);
            return "No airport could be resolved for " + destination + ". Flight options are unavailable, but the travel plan can still be generated.";
        }

        if (departureCity == null || departureCity.isBlank()) {
            log.warn("No departure city found for flight search to destination={}; skipping flight lookup", destination);
            return "No departure city was provided, so flight options are unavailable.";
        }

        log.info("Resolved flight route from {} ({}) to {} ({})", departureCityName, departureCity, destination, destinationAirportCode);
        String rawFlightData = externalApiService.fetchFlightOptions(departureCity, destinationAirportCode, departureDate);

        String returnDate = request.getReturnDate() != null ? request.getReturnDate() : "not provided";
        String flightInsights = chatClient.prompt()
            .system("You are the Flight Agent. Summarize only facts present in the supplied AviationStack response. "
                + "Never invent flight numbers, airlines, times, durations, prices, or dates. Never output placeholders such as [Insert flight number]. "
                + "When a field is missing, write 'Unavailable'. If the response contains an API error or no flight data, clearly say that flight availability is unavailable.")
            .user("Route FROM " + departureCity + " TO " + destination + ", departureDate=" + departureDate + ", returnDate=" + returnDate + ", Model=" + selectedModel +
                ". Analyze this AviationStack response and return verified flight details only:\n" + rawFlightData)
                .call()
                .content();
        log.info("Flight insights fetched for departureCity={}, destination={}", departureCity, destination);
        return flightInsights;
    }

}
