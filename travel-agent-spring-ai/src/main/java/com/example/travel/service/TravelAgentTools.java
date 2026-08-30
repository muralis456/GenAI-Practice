package com.example.travel.service;

import com.example.travel.dto.TravelRequest;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class TravelAgentTools {

    private static final Logger log = LoggerFactory.getLogger(TravelAgentTools.class);

    private final FlightAgentService flightAgentService;
    private final TravelResearchAgentService travelResearchAgentService;
    private final HotelAgentService hotelAgentService;
    private final ItineraryAgentService itineraryAgentService;
    private final ThreadLocal<Map<String, String>> toolResults = ThreadLocal.withInitial(LinkedHashMap::new);
    private final ThreadLocal<String> currentQuery = new ThreadLocal<>();

    public TravelAgentTools(FlightAgentService flightAgentService,
                            TravelResearchAgentService travelResearchAgentService,
                            HotelAgentService hotelAgentService,
                            ItineraryAgentService itineraryAgentService) {
        this.flightAgentService = flightAgentService;
        this.travelResearchAgentService = travelResearchAgentService;
        this.hotelAgentService = hotelAgentService;
        this.itineraryAgentService = itineraryAgentService;
    }

    public void startRequest(String query) {
        toolResults.get().clear();
        currentQuery.set(query);
    }

    public Map<String, String> getResults() {
        return new LinkedHashMap<>(toolResults.get());
    }

    public void endRequest() {
        toolResults.remove();
        currentQuery.remove();
    }

    @Tool(description = "Search flights FROM departureCity TO destinationCity. Keep the direction exactly as requested by the user. Pass readable city names, for example departureCity=Mumbai and destinationCity=Beijing. Pass destinationIataCode only when certain; Mumbai is BOM and Beijing is PEK. Never use LHR unless the user explicitly says London or Heathrow, and never pass a made-up code such as MUM.")
        public String searchFlights(
            @ToolParam(description = "City or airport the user is departing FROM. Never default this to London.") String departureCity,
            @ToolParam(description = "City or airport the user is travelling TO.") String destinationCity,
            @ToolParam(description = "Three-letter IATA code for destination only when certain, otherwise leave empty.", required = false) String destinationIataCode,
            @ToolParam(description = "Departure date in yyyy-MM-dd format. Leave empty to use today.", required = false) String departureDate,
            @ToolParam(description = "Return date in yyyy-MM-dd format, when provided by the user.", required = false) String returnDate,
            @ToolParam(description = "The configured chat model name.") String selectedModel) {
        departureCity = resolveDepartureCity(departureCity);
        destinationCity = resolveDestinationCity(destinationCity);
            log.info("Tool called: searchFlights(from={}, to={}, destinationIataCode={}, departureDate={}, returnDate={})",
                departureCity, destinationCity, destinationIataCode, departureDate, returnDate);
        TravelRequest request = request(destinationCity, departureCity, departureDate, selectedModel);
        request.setReturnDate(returnDate);
        String result = flightAgentService.fetchFlightInsights(request, selectedModel, destinationIataCode);
        toolResults.get().put("flightInsights", result);
            log.info("Tool completed: searchFlights(from={}, to={})", departureCity, destinationCity);
        return result;
    }

    @Tool(description = "Research a destination. Use for attractions, food, weather, local customs, restaurants, and things to do.")
    public String researchDestination(String destination, String travelStyle, String selectedModel) {
        log.info("Tool called: researchDestination(destination={}, travelStyle={})", destination, travelStyle);
        if (isBlank(destination)) {
            return missingDestination("researchDestination");
        }
        TravelRequest request = request(destination, null, null, selectedModel);
        request.setTravelStyle(travelStyle);
        String result = travelResearchAgentService.fetchTravelResearch(request, selectedModel);
        toolResults.get().put("travelInsights", result);
        log.info("Tool completed: researchDestination(destination={})", destination);
        return result;
    }

    @Tool(description = "Find hotel and accommodation recommendations for a destination. Use when the user asks for hotels, lodging, stays, or accommodation.")
    public String searchHotels(String destination, String stayDuration, String budget, String selectedModel) {
        log.info("Tool called: searchHotels(destination={}, stayDuration={}, budget={})", destination, stayDuration, budget);
        if (isBlank(destination)) {
            return missingDestination("searchHotels");
        }
        TravelRequest request = request(destination, null, null, selectedModel);
        request.setBudget(budget);
        String result = hotelAgentService.findHotels(request, selectedModel);
        toolResults.get().put("hotelInsights", result);
        log.info("Tool completed: searchHotels(destination={})", destination);
        return result;
    }

    @Tool(description = "Build a day-by-day itinerary. Use when the user asks for a travel plan, schedule, itinerary, or trip duration plan.")
    public String buildItinerary(String destination, String departureDate, String returnDate,
                                 String flightInsights, String travelInsights, String selectedModel) {
        log.info("Tool called: buildItinerary(destination={}, departureDate={}, returnDate={})",
            destination, departureDate, returnDate);
        if (isBlank(destination)) {
            return missingDestination("buildItinerary");
        }
        TravelRequest request = request(destination, null, departureDate, selectedModel);
        request.setReturnDate(returnDate);
        String result = itineraryAgentService.buildItinerary(request, selectedModel, flightInsights, travelInsights);
        toolResults.get().put("itinerary", result);
        log.info("Tool completed: buildItinerary(destination={})", destination);
        return result;
    }

    private TravelRequest request(String destination, String departureCity, String departureDate, String selectedModel) {
        TravelRequest request = new TravelRequest();
        request.setDestination(destination);
        request.setDepartureCity(departureCity);
        request.setDepartureDate(departureDate);
        request.setSelectedModel(selectedModel);
        return request;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String missingDestination(String toolName) {
        log.warn("Tool rejected: {} requires a destination", toolName);
        return "Tool error: destination is required. Extract the destination city or country from the current user request and call this tool again.";
    }

    private String resolveDepartureCity(String departureCity) {
        if (!isBlank(departureCity)) {
            return departureCity;
        }
        Matcher route = routeMatcher();
        return route.find() ? route.group(1).trim() : departureCity;
    }

    private String resolveDestinationCity(String destinationCity) {
        if (!isBlank(destinationCity)) {
            return destinationCity;
        }
        Matcher route = routeMatcher();
        return route.find() ? route.group(2).trim().replaceFirst("[.!?].*$", "") : destinationCity;
    }

    private Matcher routeMatcher() {
        String query = currentQuery.get();
        Pattern pattern = Pattern.compile("(?i)\\bfrom\\s+(.+?)\\s+to\\s+(.+?)(?:\\s+for\\s+|\\s+on\\s+|\\s+today\\b|[.!?]|$)");
        return pattern.matcher(query == null ? "" : query);
    }
}
