package com.example.travel.service;

import com.example.travel.dto.TravelRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class FlightAgentService {

    private static final Logger log = LoggerFactory.getLogger(FlightAgentService.class);

    private final ChatClient chatClient;
    private final ExternalApiService externalApiService;

    public FlightAgentService(ChatClient chatClient, ExternalApiService externalApiService) {
        this.chatClient = chatClient;
        this.externalApiService = externalApiService;
    }

    public String fetchFlightInsights(TravelRequest request, String selectedModel) {
        log.debug("Fetching flight insights for departureCity={}, destination={}, model={}", request.getDepartureCity(), request.getDestination(), selectedModel);
        String departureCity = request.getDepartureCity();
        String destination = request.getDestination();
        String departureDate = request.getDepartureDate() != null ? request.getDepartureDate() : "2026-09-15";

        String rawFlightData = externalApiService.fetchFlightOptions(departureCity, destination, departureDate);

        String flightInsights = chatClient.prompt()
                .system("You are the Flight Agent. Summarize flight availability, pricing, and timing clearly. Ignore irrelevant noise and focus on viable options. Return a compact structured summary.")
                .user("Destination=" + destination + ", DepartureCity=" + departureCity + ", Date=" + departureDate + ", Model=" + selectedModel + 
                        ". Analyze the flight data and produce useful recommendations: \n" + rawFlightData)
                .call()
                .content();
        log.info("Flight insights fetched for departureCity={}, destination={}", departureCity, destination);
        return flightInsights;
    }
}
