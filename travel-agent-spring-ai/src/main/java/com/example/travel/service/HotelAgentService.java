package com.example.travel.service;

import com.example.travel.dto.TravelRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class HotelAgentService {

    private static final Logger log = LoggerFactory.getLogger(HotelAgentService.class);

    private final ChatClient chatClient;
    private final ExternalApiService externalApiService;

    public HotelAgentService(ChatClient chatClient, ExternalApiService externalApiService) {
        this.chatClient = chatClient;
        this.externalApiService = externalApiService;
    }

    public String findHotels(TravelRequest request, String selectedModel) {
        String destination = request.getDestination();
        String query = "Best hotels in " + destination + " for a 3-day trip, including location, price range, family suitability, and guest ratings.";
        log.debug("Searching hotels for destination={}, model={}", destination, selectedModel);
        String rawResearch = externalApiService.searchTravelInfo(query);

        return chatClient.prompt()
                .system("You are the Hotel Agent. Recommend suitable hotels using only the supplied research. Include area, approximate price range, strengths, and who each hotel suits. Do not invent availability or exact prices.")
                .user("Destination=" + destination + ", Model=" + selectedModel + "\nHotel research:\n" + rawResearch)
                .call()
                .content();
    }
}
