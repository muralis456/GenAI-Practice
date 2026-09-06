package com.example.travel.service;

import com.example.travel.model.WeatherForecast;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.util.Map;

@Service
@ConditionalOnProperty(prefix = "travel.mcp.client", name = "enabled", havingValue = "true")
public class McpWeatherClient {

    private final McpToolClient client;

    public McpWeatherClient(McpToolClient client) {
        this.client = client;
    }

    public WeatherForecast forecast(String destination, LocalDate start, LocalDate end) {
        try {
            JsonNode root = client.call("get_weather", Map.of(
                    "destination", destination == null ? "" : destination,
                    "startDate", start == null ? "" : start.toString(),
                    "endDate", end == null ? "" : end.toString()));
            return new WeatherForecast(root.path("location").asString(destination),
                    root.path("summary").asString("Weather unavailable."),
                    root.path("rainLikely").asBoolean(false));
        } catch (Exception exception) {
            return new WeatherForecast(destination, "Weather lookup failed through MCP.", false);
        }
    }
}
