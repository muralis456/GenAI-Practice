package com.example.travel.mcp.service;

import com.example.travel.mcp.dto.AirportResult;
import com.example.travel.mcp.dto.HotelResult;
import com.example.travel.mcp.dto.ResearchHit;
import com.example.travel.mcp.dto.ResearchResult;
import com.example.travel.mcp.dto.SearchHotelsResponse;
import com.example.travel.mcp.dto.WeatherResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class TravelMcpServices {

    private static final Logger log = LoggerFactory.getLogger(TravelMcpServices.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String tavilyUrl;
    private final String tavilyApiKey;
    private final String geocodeUrl;
    private final String forecastUrl;

    public TravelMcpServices(RestClient.Builder builder,
                             ObjectMapper objectMapper,
                             @Value("${travel.tavily.api-url:https://api.tavily.com/search}") String tavilyUrl,
                             @Value("${travel.tavily.api-key:}") String tavilyApiKey,
                             @Value("${travel.weather.geocode-url:https://geocoding-api.open-meteo.com/v1/search}") String geocodeUrl,
                             @Value("${travel.weather.forecast-url:https://api.open-meteo.com/v1/forecast}") String forecastUrl) {
        this.restClient = builder.build();
        this.objectMapper = objectMapper;
        this.tavilyUrl = tavilyUrl;
        this.tavilyApiKey = tavilyApiKey;
        this.geocodeUrl = geocodeUrl;
        this.forecastUrl = forecastUrl;
    }

    public SearchHotelsResponse searchHotels(String destination, String travelStyle, boolean cheaper) {
        String query = (cheaper ? "Budget affordable hotels in " : "Best hotels in ") + destination
                + " including location, price range, family suitability, and guest ratings. Style=" + travelStyle;
        ResearchResult result = research(query);
        List<HotelResult> hotels = new ArrayList<>();
        for (ResearchHit hit : result.hits()) {
            hotels.add(new HotelResult(hit.title(), destination, cheaper ? "budget" : "mid-range", "", "", hit.content()));
        }
        return result.success() ? SearchHotelsResponse.success(hotels, result.message())
                : SearchHotelsResponse.failure(result.message());
    }

    public WeatherResult weather(String destination, LocalDate start, LocalDate end) {
        LocalDate from = start == null ? LocalDate.now() : start;
        LocalDate to = end == null ? from.plusDays(5) : end;
        try {
            String geoUri = UriComponentsBuilder.fromUriString(geocodeUrl)
                    .queryParam("name", destination).queryParam("count", 1).build().toUriString();
            JsonNode geo = objectMapper.readTree(restClient.get().uri(geoUri).retrieve().body(String.class));
            JsonNode first = geo.path("results").isArray() && !geo.path("results").isEmpty()
                    ? geo.path("results").get(0) : null;
            if (first == null) {
                return new WeatherResult(false, destination, "Weather unavailable for this destination.", false);
            }
            String uri = UriComponentsBuilder.fromUriString(forecastUrl)
                    .queryParam("latitude", first.path("latitude").asDouble())
                    .queryParam("longitude", first.path("longitude").asDouble())
                    .queryParam("daily", "precipitation_sum,weathercode")
                    .queryParam("start_date", from).queryParam("end_date", to)
                    .queryParam("timezone", "auto").build().toUriString();
            JsonNode forecast = objectMapper.readTree(restClient.get().uri(uri).retrieve().body(String.class));
            boolean rain = false;
            double total = 0;
            for (JsonNode value : forecast.path("daily").path("precipitation_sum")) {
                double mm = value.asDouble();
                total += mm;
                rain |= mm >= 2;
            }
            String summary = rain
                    ? "Rain likely during the stay (about " + Math.round(total) + " mm total). Prefer indoor backups on wet days."
                    : "Mostly dry conditions expected. Outdoor sightseeing is viable.";
            return new WeatherResult(true, destination, summary, rain);
        } catch (Exception exception) {
            log.warn("mcp.provider.weather failed destination={} error={}", destination, exception.getMessage());
            return new WeatherResult(false, destination, "Weather lookup failed.", false);
        }
    }

    public AirportResult resolveAirport(String cityOrCode) {
        if (cityOrCode == null || cityOrCode.isBlank()) {
            return new AirportResult(false, "", "", "", "Airport query is required.");
        }
        String value = cityOrCode.trim();
        if (value.matches("[A-Za-z]{3}")) {
            return new AirportResult(true, value.toUpperCase(Locale.ROOT), value, "", "Airport code accepted.");
        }
        Map<String, String> known = Map.ofEntries(
                Map.entry("bengaluru", "BLR"), Map.entry("bangalore", "BLR"), Map.entry("mumbai", "BOM"),
                Map.entry("delhi", "DEL"), Map.entry("new delhi", "DEL"), Map.entry("chennai", "MAA"),
                Map.entry("hyderabad", "HYD"), Map.entry("kolkata", "CCU"), Map.entry("goa", "GOI"),
                Map.entry("pune", "PNQ"), Map.entry("paris", "CDG"), Map.entry("tokyo", "NRT"),
                Map.entry("london", "LHR"), Map.entry("new york", "JFK"));
        String iata = known.get(value.toLowerCase(Locale.ROOT));
        return iata == null
                ? new AirportResult(false, "", value, "", "No airport found for " + value + ".")
                : new AirportResult(true, iata, value, "", "Airport resolved.");
    }

    public ResearchResult research(String query) {
        if (query == null || query.isBlank()) {
            return ResearchResult.failure("Research query is required.");
        }
        if (tavilyApiKey == null || tavilyApiKey.isBlank()) {
            return ResearchResult.failure("Travel research provider is not configured.");
        }
        try {
            String body = restClient.post().uri(tavilyUrl)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + tavilyApiKey)
                    .body(Map.of("query", query, "max_results", 5))
                    .retrieve().body(String.class);
            JsonNode root = objectMapper.readTree(body);
            List<ResearchHit> hits = new ArrayList<>();
            for (JsonNode result : root.path("results")) {
                hits.add(new ResearchHit(result.path("title").asString(""),
                        result.path("content").asString(""), result.path("url").asString(""),
                        result.path("score").asDouble(0)));
            }
            return ResearchResult.success(hits, "Travel research completed.");
        } catch (Exception exception) {
            log.warn("mcp.provider.tavily failed queryLength={} error={}", query.length(), exception.getMessage());
            return ResearchResult.failure("Travel research provider is unavailable.");
        }
    }
}
