package com.example.travel.service;

import com.example.travel.dto.TravelRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class TravelResearchAgentService {

    private static final Logger log = LoggerFactory.getLogger(TravelResearchAgentService.class);

    private final ChatClient chatClient;
    private final ExternalApiService externalApiService;

    public TravelResearchAgentService(ChatClient chatClient, ExternalApiService externalApiService) {
        this.chatClient = chatClient;
        this.externalApiService = externalApiService;
    }

    public String fetchTravelResearch(TravelRequest request, String selectedModel) {
        log.debug("Fetching travel research for destination={}, model={}", request.getDestination(), selectedModel);
        String destination = request.getDestination();
        String travelStyle = request.getTravelStyle() != null ? request.getTravelStyle() : "balanced";
        String query = "Best attractions, food, weather, local tips, and family-friendly experiences for " + destination + " with a " + travelStyle + " travel style.";

        String rawResearch = externalApiService.searchTravelInfo(query);

        String research = chatClient.prompt()
                .system("You are the Travel Research Agent. Extract actionable travel information: top attractions, local food, weather, best travel windows, and practical suggestions.")
                .user("Model=" + selectedModel + ". Summarize the research into a practical city guide for " + destination + "\n" + rawResearch)
                .call()
                .content();
        log.info("Travel research completed for destination={}", destination);
        return research;
    }
}
