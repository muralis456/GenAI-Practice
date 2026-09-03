package com.example.travel.graph.node;

import com.example.travel.graph.TravelGraphNodes;
import com.example.travel.graph.TravelState;
import com.example.travel.tool.AirportLookupTool;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class AirportResolverNode implements NodeAction<TravelState> {

    private final AirportLookupTool airportLookupTool;

    public AirportResolverNode(AirportLookupTool airportLookupTool) {
        this.airportLookupTool = airportLookupTool;
    }

    @Override
    public Map<String, Object> apply(TravelState state) {
        if (!state.needsFlights()) {
            return TravelState.trace(TravelGraphNodes.AIRPORT, "skip", "flights not requested");
        }
        String originQuery = TravelState.firstNonBlank(state.origin(), state.preferredAirport(), "Bengaluru");
        String destinationQuery = TravelState.firstNonBlank(state.destination());
        String originIata = airportLookupTool.resolveIata(originQuery);
        String destinationIata = airportLookupTool.resolveIata(destinationQuery);

        Map<String, Object> updates = new LinkedHashMap<>();
        // Keep a readable origin city in state when caller only had a blank/IATA preference.
        if (TravelState.isBlank(state.origin())) {
            updates.put(TravelState.ORIGIN, originQuery);
        }
        updates.put(TravelState.ORIGIN_IATA, originIata);
        updates.put(TravelState.DESTINATION_IATA, destinationIata);
        String detail = originQuery + "->" + originIata + " / " + destinationQuery + "->" + destinationIata;
        updates.putAll(TravelState.trace(TravelGraphNodes.AIRPORT,
                originIata.isBlank() || destinationIata.isBlank() ? "warn" : "ok",
                detail));
        return updates;
    }
}
