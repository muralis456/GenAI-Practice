package com.example.travel.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.util.UriComponentsBuilder;


import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;

@Service
public class ExternalApiService {

    private static final Logger log = LoggerFactory.getLogger(ExternalApiService.class);

    private final RestTemplate restTemplate;

    @Value("${travel.tavily.api-url:}")
    private String tavilyUrl;

    @Value("${travel.tavily.api-key:}")
    private String tavilyApiKey;

    @Value("${travel.aviation.api-url:}")
    private String aviationUrl;

    @Value("${travel.aviation.api-key:}")
    private String aviationApiKey;

    public ExternalApiService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public String searchTravelInfo(String query) {
        if (tavilyApiKey == null || tavilyApiKey.isBlank()) {
            log.warn("Tavily API key is missing");
            return "Tavily API key is missing. Please set TAVILY_API_KEY environment variable.";
        }

        log.debug("Searching travel info via Tavily for query={}", query);
        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");
        headers.set("Authorization", "Bearer " + tavilyApiKey);

        Map<String, Object> body = new HashMap<>();
        body.put("query", query);
        body.put("search_depth", "basic");
        body.put("max_results", 5);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        ResponseEntity<String> response = restTemplate.exchange(tavilyUrl, HttpMethod.POST, request, String.class);
        log.info("Tavily search completed. Response: {}", response.getBody());
        return response.getBody();
    }

    public String fetchFlightOptions(String departureCity, String destination, String date) {
        if (aviationApiKey == null || aviationApiKey.isBlank()) {
            log.warn("AviationStack API key is missing");
            return "AviationStack API key is missing. Please set AVIATIONSTACK_API_KEY environment variable.";
        }

        String url = UriComponentsBuilder.fromUriString(aviationUrl + "/flights")
                .queryParam("access_key", aviationApiKey)
                .queryParam("dep_iata", departureCity)
                .queryParam("arr_iata", destination)
                .build()
                .toUriString();

        log.info("Calling AviationStack flights API: from={}, to={}, date={}", departureCity, destination);
        try {
        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
        log.info("Flight options fetched for departureCity={}, destination={}. Response: {}", departureCity, destination, response.getBody());
            return extractFlightSummary(response.getBody());
        } catch (HttpStatusCodeException exception) {
            log.warn("AviationStack flights API returned status={} for from={}, to={}",
                    exception.getStatusCode(), departureCity, destination);
            return "AviationStack flight search failed with status " + exception.getStatusCode()
                    + ": " + exception.getResponseBodyAsString();
        }
    }

    private String extractFlightSummary(String responseBody) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(responseBody);
            JsonNode data = root.path("data");
            if (!data.isArray() || data.isEmpty()) {
                return "No flight records were returned by AviationStack.";
            }

            StringBuilder summary = new StringBuilder();
            int count = Math.min(data.size(), 10);
            for (int index = 0; index < count; index++) {
                JsonNode flight = data.get(index);
                summary.append("Flight ").append(index + 1)
                        .append(": status=").append(text(flight, "flight_status"))
                        .append(", date=").append(text(flight, "flight_date"))
                        .append(", flight=").append(text(flight.path("flight"), "iata"))
                        .append(", airline=").append(text(flight.path("airline"), "name"))
                        .append(", from=").append(text(flight.path("departure"), "iata"))
                        .append(", departure=").append(text(flight.path("departure"), "scheduled"))
                        .append(", to=").append(text(flight.path("arrival"), "iata"))
                        .append(", arrival=").append(text(flight.path("arrival"), "scheduled"))
                        .append("\n");
            }
            return summary.toString();
        } catch (Exception exception) {
            log.warn("Could not parse AviationStack response", exception);
            return "AviationStack returned an unreadable flight response.";
        }
    }

    private String text(JsonNode node, String field) {
        String value = node.path(field).asString(null);
        return value == null || value.isBlank() ? "Unavailable" : value;
    }
}
