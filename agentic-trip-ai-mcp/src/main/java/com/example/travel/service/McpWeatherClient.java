package com.example.travel.service;

import com.example.travel.model.WeatherForecast;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.util.Map;

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
            WeatherForecast result = new WeatherForecast(root.path("location").asString(destination),
                    root.path("summary").asString("Weather unavailable."),
                    root.path("rainLikely").asBoolean(false));
            JsonNode days = root.path("days");
            if (!days.isArray() && root.path("daily").isObject()) {
                days = root.path("daily").path("days");
            }
            if (days.isArray()) {
                java.util.List<WeatherForecast.DailyForecast> forecasts = new java.util.ArrayList<>();
                for (JsonNode day : days) {
                    forecasts.add(new WeatherForecast.DailyForecast(
                            day.path("date").asText(day.path("time").asText("")),
                            day.path("condition").asText("Forecast"),
                            day.path("icon").asText("🌤️"),
                            day.path("high").isNumber() ? day.path("high").doubleValue() : null,
                            day.path("low").isNumber() ? day.path("low").doubleValue() : null,
                            day.path("rainProbability").isNumber() ? day.path("rainProbability").intValue() : null));
                }
                result.setDays(forecasts);
            }
            return result;
        } catch (Exception exception) {
            log.error("mcp.client.error client=McpWeatherClient operation=forecast destination={} start={} end={} errorType={} errorMessage={}",
                    destination, start, end, exception.getClass().getName(), safeMessage(exception), exception);
            return new WeatherForecast(destination, "Weather lookup failed through MCP.", false);
        }
    }

    private String safeMessage(Exception exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }
}
