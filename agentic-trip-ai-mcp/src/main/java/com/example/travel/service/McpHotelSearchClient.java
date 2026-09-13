package com.example.travel.service;

import com.example.travel.model.HotelOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@ConditionalOnProperty(prefix = "travel.mcp.client", name = "enabled", havingValue = "true")
public class McpHotelSearchClient {

    private static final Logger log = LoggerFactory.getLogger(McpHotelSearchClient.class);

    private final McpToolClient client;

    public McpHotelSearchClient(McpToolClient client) {
        this.client = client;
    }

    public List<HotelOption> search(String destination, String travelStyle, boolean cheaper) {
        return search(destination, travelStyle, cheaper, null);
    }

    public List<HotelOption> search(String destination, String travelStyle, boolean cheaper, java.math.BigDecimal hotelBudget) {
        try {
            String budgetText = hotelBudget == null ? "" : ", hotel budget ceiling INR=" + hotelBudget.toPlainString();
            Map<String, Object> arguments = new java.util.LinkedHashMap<>();
            arguments.put("destination", destination == null ? "" : destination);
            arguments.put("travelStyle", travelStyle == null ? "balanced" : travelStyle);
            arguments.put("cheaper", cheaper);
            if (hotelBudget != null) {
                arguments.put("hotelBudget", hotelBudget);
            }
            String userInput = "Find real, identifiable hotel properties for destination "
                    + (destination == null ? "" : destination)
                    + ", travel style " + (travelStyle == null ? "balanced" : travelStyle)
                    + ", cheaper=" + cheaper + budgetText
                    + ". Return hotel properties only; do not return article titles, listicles, guides or generic research headings.";
            JsonNode root = client.callByUserInput("Find accommodation/hotels", userInput, arguments);
            log.debug("mcp.hotel.raw-response destination={} payload={}", destination, abbreviate(root == null ? "" : root.toString()));
            List<HotelOption> hotels = new ArrayList<>();
            List<JsonNode> records = hotelRecords(root);
            if (records.isEmpty()) {
                log.warn("mcp.hotel.no-structured-records destination={} responseKeys={}", destination, root == null ? "null" : root.toString());
            }
            for (JsonNode node : records) {
                HotelOption hotel = new HotelOption();
                hotel.setName(node.path("name").asString(""));
                hotel.setArea(node.path("area").asString(""));
                hotel.setPriceRange(node.path("priceRange").asString(""));
                hotel.setRating(node.path("rating").asString(""));
                hotel.setSuitableFor(node.path("suitableFor").asString(""));
                hotel.setNotes(node.path("notes").asString(""));
                hotels.add(hotel);
                log.debug("mcp.hotel.raw-record destination={} name={} area={} priceRange={}",
                        destination, abbreviate(hotel.getName()), abbreviate(hotel.getArea()),
                        abbreviate(hotel.getPriceRange()));
            }
            return hotels;
        } catch (Exception exception) {
            log.error("mcp.client.error client=McpHotelSearchClient operation=search destination={} cheaper={} errorType={} errorMessage={}",
                    destination, cheaper, exception.getClass().getName(), safeMessage(exception), exception);
            return List.of();
        }
    }

    private List<JsonNode> hotelRecords(JsonNode root) {
        if (root == null || root.isMissingNode() || root.isNull()) {
            return List.of();
        }
        JsonNode candidates = root.path("hotels");
        if (!candidates.isArray()) candidates = root.path("results");
        if (!candidates.isArray()) candidates = root.path("data").path("hotels");
        if (!candidates.isArray()) candidates = root.path("data").path("results");
        if (!candidates.isArray()) return List.of();
        List<JsonNode> result = new ArrayList<>();
        candidates.forEach(result::add);
        return result;
    }

    private String abbreviate(String value) {
        if (value == null) return "";
        String normalized = value.replace("\n", " ").trim();
        return normalized.length() <= 120 ? normalized : normalized.substring(0, 120) + "...";
    }

    private String safeMessage(Exception exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }
}
