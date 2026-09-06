package com.example.travel.tool;

import com.example.travel.model.WeatherForecast;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;

@Component
public class WeatherTool {

    private static final Logger log = LoggerFactory.getLogger(WeatherTool.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String geocodeUrl;
    private final String forecastUrl;

    public WeatherTool(RestTemplate restTemplate,
                       ObjectMapper objectMapper,
                       @Value("${travel.weather.geocode-url:https://geocoding-api.open-meteo.com/v1/search}") String geocodeUrl,
                       @Value("${travel.weather.forecast-url:https://api.open-meteo.com/v1/forecast}") String forecastUrl) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.geocodeUrl = geocodeUrl;
        this.forecastUrl = forecastUrl;
    }

    public WeatherForecast forecast(String destination, LocalDate start, LocalDate end) {
        LocalDate from = start == null ? LocalDate.now() : start;
        LocalDate to = end == null ? from.plusDays(5) : end;
        DateWindow window = clampToOpenMeteo(from, to);
        try {
            String geoUrl = UriComponentsBuilder.fromUriString(geocodeUrl)
                    .queryParam("name", destination)
                    .queryParam("count", 1)
                    .build()
                    .toUriString();
            JsonNode geo = objectMapper.readTree(restTemplate.getForObject(geoUrl, String.class));
            JsonNode results = geo.path("results");
            if (!results.isArray() || results.isEmpty()) {
                return new WeatherForecast(destination, "Weather unavailable for this destination.", false);
            }
            double lat = number(results.get(0).path("latitude"));
            double lon = number(results.get(0).path("longitude"));
            String forecastUri = UriComponentsBuilder.fromUriString(forecastUrl)
                    .queryParam("latitude", lat)
                    .queryParam("longitude", lon)
                    .queryParam("daily", "precipitation_sum,weathercode")
                    .queryParam("start_date", window.from)
                    .queryParam("end_date", window.to)
                    .queryParam("timezone", "auto")
                    .build()
                    .toUriString();
            JsonNode forecast = objectMapper.readTree(restTemplate.getForObject(forecastUri, String.class));
            JsonNode precip = forecast.path("daily").path("precipitation_sum");
            boolean rain = false;
            double total = 0;
            if (precip.isArray()) {
                for (JsonNode value : precip) {
                    double mm = number(value);
                    total += mm;
                    if (mm >= 2) {
                        rain = true;
                    }
                }
            }
            String summary = rain
                    ? "Rain likely during the stay (about " + Math.round(total) + " mm total). Prefer indoor backups on wet days."
                    : "Mostly dry conditions expected. Outdoor sightseeing is viable.";
            if (window.clamped) {
                summary += " Open-Meteo only covers " + window.from + " to " + window.to
                        + " (not the full requested trip dates).";
            }
            return new WeatherForecast(destination, summary, rain);
        } catch (Exception exception) {
            log.warn("Weather lookup failed for {}: {}", destination, exception.getMessage());
            return new WeatherForecast(destination, "Weather lookup failed.", false);
        }
    }

    @Tool(description = "Get Open-Meteo weather forecast for a destination between two dates (yyyy-MM-dd).")
    public String forecastText(
            @ToolParam(description = "Destination city or country") String destination,
            @ToolParam(description = "Start date yyyy-MM-dd") String startDate,
            @ToolParam(description = "End date yyyy-MM-dd") String endDate) {
        LocalDate start = parseDate(startDate, LocalDate.now());
        LocalDate end = parseDate(endDate, start.plusDays(5));
        return forecast(destination, start, end).toDisplay();
    }

    private DateWindow clampToOpenMeteo(LocalDate start, LocalDate end) {
        LocalDate min = LocalDate.now().minusDays(90);
        LocalDate max = LocalDate.now().plusDays(15);
        LocalDate from = start.isBefore(min) ? min : start;
        LocalDate to = end.isAfter(max) ? max : end;
        if (from.isAfter(max)) {
            from = max.minusDays(6);
            to = max;
        }
        if (from.isBefore(min)) {
            from = min;
        }
        if (to.isBefore(from)) {
            to = from.plusDays(5);
            if (to.isAfter(max)) {
                to = max;
            }
        }
        if (to.isAfter(max)) {
            to = max;
        }
        boolean clamped = !from.equals(start) || !to.equals(end);
        return new DateWindow(from, to, clamped);
    }

    private record DateWindow(LocalDate from, LocalDate to, boolean clamped) {
    }

    private LocalDate parseDate(String value, LocalDate fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private double number(JsonNode node) {
        String value = node.asString("0");
        try {
            return Double.parseDouble(value);
        } catch (Exception ignored) {
            return 0;
        }
    }
}
