package com.example.travel.service;

import com.example.travel.graph.TravelState;
import com.example.travel.model.FlightOption;
import com.example.travel.tool.AirportLookupTool;
import com.example.travel.tool.FlightSearchTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class FlightAgentService {

    private static final Logger log = LoggerFactory.getLogger(FlightAgentService.class);

    private final FlightSearchTool flightSearchTool;
    private final AirportLookupTool airportLookupTool;

    public FlightAgentService(FlightSearchTool flightSearchTool, AirportLookupTool airportLookupTool) {
        this.flightSearchTool = flightSearchTool;
        this.airportLookupTool = airportLookupTool;
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
        // Deterministic @Tool-backed call (AviationStack with flight_date).
        List<FlightOption> flights = flightSearchTool.search(originIata, destinationIata, state.departureDate());
        if (flights == null || flights.isEmpty()) {
            return FlightSearchResult.unavailable(originIata, destinationIata, "No flights were returned.");
        }
        return new FlightSearchResult(originIata, destinationIata, flights);
    }

    public record FlightSearchResult(String originIata, String destinationIata, List<FlightOption> flights) {
        static FlightSearchResult unavailable(String originIata, String destinationIata, String message) {
            FlightOption option = new FlightOption();
            option.setNotes(message);
            option.setStatus("unavailable");
            List<FlightOption> flights = new ArrayList<>();
            flights.add(option);
            return new FlightSearchResult(originIata, destinationIata, flights);
        }
    }
}
