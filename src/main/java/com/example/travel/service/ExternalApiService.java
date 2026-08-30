package com.example.travel.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;

@Service
public class ExternalApiService {

    private static final Logger log = LoggerFactory.getLogger(ExternalApiService.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${travel.tavily.api-url:}")
    private String tavilyUrl;

    @Value("${travel.tavily.api-key:}")
    private String tavilyApiKey;

    @Value("${travel.aviation.api-url:}")
    private String aviationUrl;

    @Value("${travel.aviation.api-key:}")
    private String aviationApiKey;

    public ExternalApiService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
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
        log.info("Tavily search completed");
        return response.getBody();
    }

    public String fetchFlightOptions(String departureCity, String destination, String date) {
        if (aviationApiKey == null || aviationApiKey.isBlank()) {
            log.warn("AviationStack API key is missing");
            return "AviationStack API key is missing. Please set AVIATIONSTACK_API_KEY environment variable.";
        }

        log.debug("Fetching flight options for departureCity={}, destination={}, date={}", departureCity, destination, date);
        String url = aviationUrl + "/flights?access_key=" + aviationApiKey
                + "&dep_iata=" + departureCity
                + "&arr_iata=" + destination
                + "&flight_date=" + date;

        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
        log.info("Flight options fetched for departureCity={}, destination={}", departureCity, destination);
        return response.getBody();
    }
}
