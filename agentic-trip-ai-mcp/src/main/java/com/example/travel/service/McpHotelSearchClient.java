package com.example.travel.service;

import com.example.travel.model.HotelOption;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@ConditionalOnProperty(prefix = "travel.mcp.client", name = "enabled", havingValue = "true")
public class McpHotelSearchClient {

    private final McpToolClient client;

    public McpHotelSearchClient(McpToolClient client) {
        this.client = client;
    }

    public List<HotelOption> search(String destination, String travelStyle, boolean cheaper) {
        try {
            JsonNode root = client.callByUserInput("Find accommodation/hotels", "Find hotel accommodation for destination " + (destination == null ? "" : destination) + ", travel style " + (travelStyle == null ? "balanced" : travelStyle) + ", cheaper=" + cheaper, Map.of(
                    "destination", destination == null ? "" : destination,
                    "travelStyle", travelStyle == null ? "balanced" : travelStyle,
                    "cheaper", cheaper));
            List<HotelOption> hotels = new ArrayList<>();
            for (JsonNode node : root.path("hotels")) {
                HotelOption hotel = new HotelOption();
                hotel.setName(node.path("name").asString(""));
                hotel.setArea(node.path("area").asString(""));
                hotel.setPriceRange(node.path("priceRange").asString(""));
                hotel.setRating(node.path("rating").asString(""));
                hotel.setSuitableFor(node.path("suitableFor").asString(""));
                hotel.setNotes(node.path("notes").asString(""));
                hotels.add(hotel);
            }
            return hotels;
        } catch (Exception exception) {
            return List.of();
        }
    }
}
