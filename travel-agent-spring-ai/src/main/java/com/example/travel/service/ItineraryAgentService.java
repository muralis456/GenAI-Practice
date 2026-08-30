package com.example.travel.service;

import com.example.travel.dto.TravelRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class ItineraryAgentService {

    private static final Logger log = LoggerFactory.getLogger(ItineraryAgentService.class);

    private final ChatClient chatClient;

    public ItineraryAgentService(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public String buildItinerary(TravelRequest request, String selectedModel, String flightInsights, String travelInsights) {
        log.debug("Building itinerary for destination={}, model={}", request.getDestination(), selectedModel);
        String destination = request.getDestination();
        LocalDate today = LocalDate.now();
        String departureDate = request.getDepartureDate() != null ? request.getDepartureDate() : today.toString();
        String returnDate = request.getReturnDate() != null ? request.getReturnDate() : today.plusDays(5).toString();

        String itinerary = chatClient.prompt()
                .system("You are the Itinerary Agent. Build a practical, day-by-day travel plan with sequence, activities, and pacing.")
                .user("Model=" + selectedModel + ", destination=" + destination + ", departureDate=" + departureDate + ", returnDate=" + returnDate + 
                        ". Create a 4-day or 5-day itinerary using this flight and travel data: \n---FLIGHT---\n" + flightInsights + "\n---RESEARCH---\n" + travelInsights)
                .call()
                .content();
        log.info("Itinerary built for destination={}", destination);
        return itinerary;
    }
}
