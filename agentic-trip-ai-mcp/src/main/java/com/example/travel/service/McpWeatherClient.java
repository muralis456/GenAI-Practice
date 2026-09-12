package com.example.travel.service;

import com.example.travel.model.WeatherForecast;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
@ConditionalOnProperty(prefix = "travel.mcp.client", name = "enabled", havingValue = "true")
public class McpWeatherClient {

    private static final Logger log = LoggerFactory.getLogger(McpWeatherClient.class);
    private final McpToolClient client;

    public McpWeatherClient(McpToolClient client) {
        this.client = client;
    }

    public WeatherForecast forecast(String destination, LocalDate start, LocalDate end) {
        return forecast(destination, start, end, "Weather forecast for " + destination);
    }

    public WeatherForecast forecast(String destination, LocalDate start, LocalDate end, String userInput) {
        try {
            JsonNode root = client.callByUserInput("Weather forecast for a travel destination", userInput, Map.of(
                    "destination", destination == null ? "" : destination,
                    "startDate", start == null ? "" : start.toString(),
                    "endDate", end == null ? "" : end.toString()));
            return new WeatherForecast(root.path("location").asString(destination),
                    root.path("summary").asString("Weather unavailable."),
                    root.path("rainLikely").asBoolean(false));
        } catch (Exception exception) {
            log.warn("MCP weather lookup failed destination={} start={} end={} error={}",
                    destination, start, end, exception.getMessage());
            return new WeatherForecast(destination, "Weather lookup failed through MCP.", false);
        }
    }
}
