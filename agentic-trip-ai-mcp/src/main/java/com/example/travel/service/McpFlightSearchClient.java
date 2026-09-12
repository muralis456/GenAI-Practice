package com.example.travel.service;

import com.example.travel.model.FlightOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Thin MCP client adapter used during the incremental flight-tool migration.
 * The LangGraph graph still decides when this capability is invoked.
 */
@Service
@ConditionalOnProperty(prefix = "travel.mcp.client", name = "enabled", havingValue = "true")
public class McpFlightSearchClient {

    private static final Logger log = LoggerFactory.getLogger(McpFlightSearchClient.class);

    private final McpToolClient mcpToolClient;
    private final ObjectMapper objectMapper;

    public McpFlightSearchClient(McpToolClient mcpToolClient, ObjectMapper objectMapper) {
        this.mcpToolClient = mcpToolClient;
        this.objectMapper = objectMapper;
    }

    public List<FlightOption> search(String origin, String destination, LocalDate departureDate, int passengers) {
        return search(origin, destination, departureDate, passengers, "Flight search for " + origin + " to " + destination);
    }

    public List<FlightOption> search(String origin, String destination, LocalDate departureDate, int passengers, String userInput) {
        try {
                Map<String, Object> input = Map.of(
                    "origin", origin,
                    "destination", destination,
                    "departureDate", departureDate == null ? "" : departureDate.toString(),
                    "returnDate", "",
                    "passengers", Math.max(1, passengers));
                return parse(mcpToolClient.callByUserInput("Live flight schedule search", userInput, input));
        } catch (Exception exception) {
            log.warn("MCP search_flights failed for {} -> {}: {}", origin, destination, exception.getMessage());
            return List.of(unavailable("MCP flight search is unavailable: " + safeMessage(exception)));
        }
    }

    private List<FlightOption> parse(JsonNode root) throws Exception {
        if (!root.path("success").asBoolean(false)) {
            return List.of(unavailable(root.path("message").asString("MCP flight search failed.")));
        }

        List<FlightOption> flights = new ArrayList<>();
        for (JsonNode flight : root.path("flights")) {
            FlightOption option = new FlightOption();
            option.setFlightNumber(flight.path("flightNumber").asString(""));
            option.setAirline(flight.path("airline").asString(""));
            option.setOrigin(flight.path("origin").asString(""));
            option.setDestination(flight.path("destination").asString(""));
            option.setDepartureTime(flight.path("departureScheduled").asString(""));
            option.setArrivalTime(flight.path("arrivalScheduled").asString(""));
            option.setStatus(flight.path("status").asString("unknown"));
            option.setNotes(flight.path("notes").asString(""));
            flights.add(option);
        }
        return flights;
    }

    private FlightOption unavailable(String message) {
        FlightOption option = new FlightOption();
        option.setStatus("unavailable");
        option.setNotes(message);
        return option;
    }

    private String safeMessage(Exception exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }
}
