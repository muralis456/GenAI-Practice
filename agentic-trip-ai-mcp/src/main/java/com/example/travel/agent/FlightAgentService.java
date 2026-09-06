package com.example.travel.agent;

import com.example.travel.graph.TravelState;
import com.example.travel.model.FlightOption;
import com.example.travel.tool.AirportLookupTool;
import com.example.travel.tool.FlightSearchTool;
import com.example.travel.service.McpFlightSearchClient;
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
        String originIata = TravelState.firstNonBlank(state.originIata(), airportLookupTool.resolveIata(state.origin()));
        String destinationIata = TravelState.firstNonBlank(state.destinationIata(),
                airportLookupTool.resolveIata(state.destination()));

        if (TravelState.isBlank(destinationIata)) {
            log.warn("No airport found for destination={}", state.destination());
            return FlightSearchResult.unavailable(originIata, destinationIata,
                    "No airport could be resolved for " + state.destination() + ". Flight options are unavailable.");
        }
        if (TravelState.isBlank(originIata)) {
            log.warn("No departure city found for flight search to destination={}", state.destination());
            return FlightSearchResult.unavailable(originIata, destinationIata,
                    "No departure city was provided, so flight options are unavailable.");
        }

        log.info("Flight agent searching {} -> {} on {}", originIata, destinationIata, state.departureDate());
        McpFlightSearchClient mcpClient = mcpFlightSearchClient.getIfAvailable();
        List<FlightOption> flights = mcpClient == null
                ? flightSearchTool.search(originIata, destinationIata, state.departureDate())
                : mcpClient.search(originIata, destinationIata, state.departureDate(), state.travelers());
        if (flights == null || flights.isEmpty()) {
            return FlightSearchResult.unavailable(originIata, destinationIata, "No flights were returned.");
        }
        return new FlightSearchResult(originIata, destinationIata, flights);
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
