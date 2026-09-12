package com.example.travel.agent;

import com.example.travel.graph.TravelState;
import com.example.travel.model.FlightOption;
import com.example.travel.tool.AirportLookupTool;
import com.example.travel.tool.FlightSearchTool;
import com.example.travel.service.McpFlightSearchClient;
import com.example.travel.support.TripSlotHeuristics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class FlightAgentService {

    private static final Logger log = LoggerFactory.getLogger(FlightAgentService.class);

    private final FlightSearchTool flightSearchTool;
    private final AirportLookupTool airportLookupTool;
    private final ObjectProvider<McpFlightSearchClient> mcpFlightSearchClient;

    public FlightAgentService(FlightSearchTool flightSearchTool,
                              AirportLookupTool airportLookupTool,
                              ObjectProvider<McpFlightSearchClient> mcpFlightSearchClient) {
        this.flightSearchTool = flightSearchTool;
        this.airportLookupTool = airportLookupTool;
        this.mcpFlightSearchClient = mcpFlightSearchClient;
    }

    public FlightSearchResult search(TravelState state) {
        String originQuery = TravelState.firstNonBlank(state.origin(), state.preferredAirport(), "Bengaluru");
        String destinationHint = TripSlotHeuristics.extractDestinationHint(state.userRequest());
        String destinationQuery = TripSlotHeuristics.normalizePlace(
                resolveDestinationQuery(state.destination(), destinationHint));

        String originIata = TravelState.firstNonBlank(state.originIata(), airportLookupTool.resolveIata(originQuery));
        String destinationIata = TravelState.firstNonBlank(state.destinationIata(),
                airportLookupTool.resolveIata(destinationQuery));

        if (TravelState.isBlank(destinationIata)) {
            log.warn("No airport found for destination={}", destinationQuery);
            return FlightSearchResult.unavailable(originIata, destinationIata,
                    "No airport could be resolved for " + destinationQuery + ". Flight options are unavailable.");
        }
        if (TravelState.isBlank(originIata)) {
            log.warn("No departure city found for flight search to destination={}", destinationQuery);
            return FlightSearchResult.unavailable(originIata, destinationIata,
                    "No departure city was provided, so flight options are unavailable.");
        }

        log.info("Flight agent searching {} -> {} on {}", originIata, destinationIata, state.departureDate());
        McpFlightSearchClient mcpClient = mcpFlightSearchClient.getIfAvailable();
        List<FlightOption> flights = mcpClient == null
                ? flightSearchTool.search(originIata, destinationIata, state.departureDate())
                : mcpClient.search(originIata, destinationIata, state.departureDate(), state.travelers(), state.userRequest());
        if (flights == null || flights.isEmpty()) {
            return new FlightSearchResult(originIata, destinationIata, List.of());
        }
        return new FlightSearchResult(originIata, destinationIata, flights);
    }

    private String resolveDestinationQuery(String destination, String hint) {
        // A destination extracted from the CURRENT request is authoritative.
        // It prevents stale/over-broad planner text such as
        // "dubai within the budget 2L" from reaching airport lookup.
        String requestDestination = TripSlotHeuristics.normalizePlace(hint);
        if (!TravelState.isBlank(requestDestination)) {
            return requestDestination;
        }
        return TripSlotHeuristics.normalizePlace(destination);
    }

    /** Explicit result type (IDE-friendly; avoids flaky nested-record resolution). */
    public static final class FlightSearchResult {
        private final String originIata;
        private final String destinationIata;
        private final List<FlightOption> flights;

        public FlightSearchResult(String originIata, String destinationIata, List<FlightOption> flights) {
            this.originIata = originIata == null ? "" : originIata;
            this.destinationIata = destinationIata == null ? "" : destinationIata;
            this.flights = flights == null ? List.of() : List.copyOf(flights);
        }

        public String originIata() {
            return originIata;
        }

        public String destinationIata() {
            return destinationIata;
        }

        public List<FlightOption> flights() {
            return flights;
        }

        static FlightSearchResult unavailable(String originIata, String destinationIata, String message) {
            FlightOption option = new FlightOption();
            option.setNotes(message);
            option.setStatus("unavailable");
            return new FlightSearchResult(originIata, destinationIata, List.of(option));
        }
    }
}
