package com.example.travel.graph.node;

import com.example.travel.graph.TravelGraphNodes;
import com.example.travel.graph.TravelState;
import com.example.travel.model.ProvenanceEvent;
import com.example.travel.agent.FlightAgentService;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class FlightNode implements NodeAction<TravelState> {

    private final FlightAgentService flightAgentService;

    public FlightNode(FlightAgentService flightAgentService) {
        this.flightAgentService = flightAgentService;
    }

    @Override
    public Map<String, Object> apply(TravelState state) {
        if (!state.needsFlights()) {
            Map<String, Object> skip = new LinkedHashMap<>();
            skip.put(TravelState.FLIGHTS, List.of());
            skip.putAll(TravelState.trace(TravelGraphNodes.FLIGHT, "skip", "not requested"));
            return skip;
        }
        FlightAgentService.FlightSearchResult result = flightAgentService.search(state);
        Map<String, Object> updates = new LinkedHashMap<>();
        updates.put(TravelState.FLIGHTS, result.flights());
        if (!TravelState.isBlank(result.originIata())) {
            updates.put(TravelState.ORIGIN_IATA, result.originIata());
        }
        if (!TravelState.isBlank(result.destinationIata())) {
            updates.put(TravelState.DESTINATION_IATA, result.destinationIata());
        }
        updates.putAll(TravelState.trace(TravelGraphNodes.FLIGHT, "ok",
                result.originIata() + " -> " + result.destinationIata() + " on " + state.departureDate()));
        updates.putAll(TravelState.provenance(new ProvenanceEvent(
                "flights", "AviationStack", "", 1.0, result.originIata() + "->" + result.destinationIata())));
        return updates;
    }
}
