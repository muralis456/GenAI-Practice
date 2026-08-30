package com.example.travel.service;

import com.example.travel.dto.TravelRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class FinalPlannerAgentService {

    private static final Logger log = LoggerFactory.getLogger(FinalPlannerAgentService.class);

    private final ChatClient chatClient;

    public FinalPlannerAgentService(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public String buildFinalPlan(TravelRequest request, String selectedModel, String flightInsights, String travelInsights, String itinerary) {
        log.debug("Building final plan for userId={}, destination={}", request.getUserId(), request.getDestination());
        String finalPlan = chatClient.prompt()
                .system("You are the Final Planner Agent. Combine all agent outputs into a polished, user-ready trip plan with summary, budget, itinerary, and recommendations. Format clearly for the front-end.")
                .user("Model=" + selectedModel + ", userId=" + request.getUserId() + ", departureCity=" + request.getDepartureCity() + ", destination=" + request.getDestination() + 
                        ", budget=" + (request.getBudget() != null ? request.getBudget() : "medium") + ", travelStyle=" + (request.getTravelStyle() != null ? request.getTravelStyle() : "balanced") + ", preferences=" + (request.getPreferences() != null ? request.getPreferences() : "") +
                        ". Combine the following outputs into the final user-facing answer: \n---FLIGHT---\n" + flightInsights + "\n---TRAVEL---\n" + travelInsights + "\n---ITINERARY---\n" + itinerary)
                .call()
                .content();
        log.info("Final plan built for userId={}", request.getUserId());
        return finalPlan;
    }
}
