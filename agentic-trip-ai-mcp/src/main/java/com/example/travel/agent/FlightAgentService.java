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

        log.info("Flight agent searching roundTrip={} datesFlexible={} {} -> {}",
                state.roundTrip(), state.datesFlexible(), originIata, destinationIata);
        McpFlightSearchClient mcpClient = mcpFlightSearchClient.getIfAvailable();
        java.time.LocalDate outboundDate = state.datesFlexible() ? null : state.departureDate();
        List<FlightOption> flights = mcpClient == null
                ? flightSearchTool.search(originIata, destinationIata, outboundDate)
                : mcpClient.search(originIata, destinationIata, outboundDate, state.travelers(), state.userRequest());
        flights = normalizeResults(flights, "outbound", outboundDate, state.datesFlexible());

        if (state.roundTrip()) {
            java.time.LocalDate returnDate = state.datesFlexible() ? null : state.returnDate();
            List<FlightOption> returns = mcpClient == null
                    ? flightSearchTool.search(destinationIata, originIata, returnDate)
                    : mcpClient.search(destinationIata, originIata, returnDate, state.travelers(),
                    state.userRequest() + " Return journey");
            flights.addAll(normalizeResults(returns, "return", returnDate, state.datesFlexible()));
        }

        if (flights.isEmpty()) {
            return new FlightSearchResult(originIata, destinationIata, List.of());
        }
        return new FlightSearchResult(originIata, destinationIata, flights);
    }

    private List<FlightOption> normalizeResults(List<FlightOption> input, String direction,
                                                 java.time.LocalDate requestedDate, boolean flexible) {
        if (input == null) {
            return new java.util.ArrayList<>();
        }
        List<FlightOption> result = new java.util.ArrayList<>();
        for (FlightOption flight : input) {
            if (flight == null) continue;
            if ("unavailable".equalsIgnoreCase(flight.getStatus())) {
                // Keep one provider message only; the UI should not display it as a flight card.
                if (result.stream().noneMatch(f -> "unavailable".equalsIgnoreCase(f.getStatus()))) {
                    flight.setDirection(direction);
                    result.add(flight);
                }
                continue;
            }
            flight.setDirection(direction);
            flight.setRequestedDate(requestedDate == null ? "" : requestedDate.toString());
            if (flexible) {
                flight.setNotes(mergeNote(flight.getNotes(), "live schedule · date flexible"));
            } else if (requestedDate != null) {
                String current = flight.getNotes() == null ? "" : flight.getNotes();
                if (!current.toLowerCase(java.util.Locale.ROOT).contains("date=" + requestedDate)) {
                    flight.setNotes(mergeNote(current, "requested date " + requestedDate + " not independently confirmed"));
                }
            }
            result.add(flight);
        }
        return result;
    }

    private String mergeNote(String existing, String addition) {
        if (existing == null || existing.isBlank()) return addition;
        if (existing.contains(addition)) return existing;
        return existing + " · " + addition;
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
