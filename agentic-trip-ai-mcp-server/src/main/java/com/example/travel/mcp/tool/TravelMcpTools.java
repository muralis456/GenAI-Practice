package com.example.travel.mcp.tool;

import com.example.travel.mcp.dto.AirportResult;
import com.example.travel.mcp.dto.ResearchResult;
import com.example.travel.mcp.dto.SearchHotelsResponse;
import com.example.travel.mcp.dto.WeatherResult;
import com.example.travel.mcp.service.TravelMcpServices;
import com.example.travel.mcp.service.McpToolGovernance;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.math.BigDecimal;

@Component
public class TravelMcpTools {

    private static final Logger log = LoggerFactory.getLogger(TravelMcpTools.class);

    private final TravelMcpServices services;
    private final McpToolGovernance governance;

    public TravelMcpTools(TravelMcpServices services, McpToolGovernance governance) {
        this.services = services;
        this.governance = governance;
    }

    @McpTool(name = "search_hotels", description = "Search live hotel properties using SerpApi Google Hotels with structured prices, ratings, images, amenities and booking links.")
    public SearchHotelsResponse searchHotels(
            @McpToolParam(description = "Destination city or country", required = true) String destination,
            @McpToolParam(description = "Travel style such as balanced, family, luxury, budget", required = false) String travelStyle,
            @McpToolParam(description = "Prefer lower priced hotels", required = false) Boolean cheaper,
            @McpToolParam(description = "Hotel check-in date yyyy-MM-dd", required = false) String checkInDate,
            @McpToolParam(description = "Hotel check-out date yyyy-MM-dd", required = false) String checkOutDate,
            @McpToolParam(description = "Number of adults", required = false) Integer adults,
            @McpToolParam(description = "Number of children", required = false) Integer children,
            @McpToolParam(description = "Maximum price per night in configured currency", required = false) BigDecimal maxPricePerNight) {
        governance.check("search_hotels");
        long started = System.nanoTime();
        LocalDate checkIn = parse(checkInDate, LocalDate.now().plusDays(1));
        LocalDate checkOut = parse(checkOutDate, checkIn.plusDays(1));
        int adultCount = adults == null ? 2 : Math.max(1, adults);
        int childCount = children == null ? 0 : Math.max(0, children);
        log.info("mcp.tool.request name=search_hotels destination={} travelStyle={} cheaper={} checkIn={} checkOut={} adults={} children={} maxPricePerNight={}",
                destination, travelStyle, cheaper, checkIn, checkOut, adultCount, childCount, maxPricePerNight);
        SearchHotelsResponse response = services.searchHotels(destination, travelStyle == null ? "balanced" : travelStyle,
                Boolean.TRUE.equals(cheaper), checkIn, checkOut, adultCount, childCount, maxPricePerNight);
        log.info("mcp.tool.response name=search_hotels success={} results={} message={}", response.success(), response.hotels().size(), response.message());
        log.info("mcp.tool.complete name=search_hotels success={} results={} durationMs={}", response.success(), response.hotels().size(), elapsedMs(started));
        return response;
    }

    @McpTool(name = "get_weather", description = "Get weather forecast for a destination and date range.")
    public WeatherResult getWeather(
            @McpToolParam(description = "Destination city or country", required = true) String destination,
            @McpToolParam(description = "Start date yyyy-MM-dd", required = false) String startDate,
            @McpToolParam(description = "End date yyyy-MM-dd", required = false) String endDate) {
        governance.check("get_weather");
        long started = System.nanoTime();
        LocalDate start = parse(startDate, LocalDate.now());
        LocalDate end = parse(endDate, start.plusDays(5));
        log.info("mcp.tool.request name=get_weather destination={} startDate={} endDate={}", destination, start, end);
        WeatherResult response = services.weather(destination, start, end);
        log.info("mcp.tool.response name=get_weather response={}", response);
        log.info("mcp.tool.complete name=get_weather success={} durationMs={}", response.success(), elapsedMs(started));
        return response;
    }

    @McpTool(name = "resolve_airport", description = "Resolve a city or airport code to a three-letter IATA airport code.")
    public AirportResult resolveAirport(
            @McpToolParam(description = "City, country, or airport code", required = true) String cityOrCode) {
        governance.check("resolve_airport");
        long started = System.nanoTime();
        log.info("mcp.tool.request name=resolve_airport query={}", cityOrCode);
        log.info("mcp.tool.start name=resolve_airport query={}", cityOrCode);
        AirportResult response = services.resolveAirport(cityOrCode);
        log.info("mcp.tool.response name=resolve_airport response={}", response);
        log.info("mcp.tool.complete name=resolve_airport success={} iata={} durationMs={}",
            response.success(), response.iata(), elapsedMs(started));
        return response;
    }

    @McpTool(name = "search_travel_research", description = "Search live destination, attraction, food, and local travel information.")
    public ResearchResult searchTravelResearch(
            @McpToolParam(description = "Concrete non-empty travel research query", required = true) String query) {
        governance.check("search_travel_research");
        long started = System.nanoTime();
        log.info("mcp.tool.request name=search_travel_research query={}", query);
        log.info("mcp.tool.start name=search_travel_research queryLength={}", query == null ? 0 : query.length());
        ResearchResult response = services.research(query);
        log.info("mcp.tool.response name=search_travel_research response={}", response);
        log.info("mcp.tool.complete name=search_travel_research success={} results={} durationMs={}",
            response.success(), response.hits().size(), elapsedMs(started));
        return response;
    }

    private long elapsedMs(long started) {
        return (System.nanoTime() - started) / 1_000_000L;
    }

    private LocalDate parse(String value, LocalDate fallback) {
        try {
            return value == null || value.isBlank() ? fallback : LocalDate.parse(value.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
