package com.example.travel.graph.node;

import com.example.travel.graph.TravelGraphNodes;
import com.example.travel.graph.TravelState;
import com.example.travel.model.ProvenanceEvent;
import com.example.travel.agent.HotelAgentService;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class HotelNode implements NodeAction<TravelState> {

    private final HotelAgentService hotelAgentService;

    public HotelNode(HotelAgentService hotelAgentService) {
        this.hotelAgentService = hotelAgentService;
    }

    @Override
    public Map<String, Object> apply(TravelState state) {
        if (!state.needsHotels()) {
            Map<String, Object> skip = new LinkedHashMap<>();
            skip.put(TravelState.HOTELS, List.of());
            skip.putAll(TravelState.trace(TravelGraphNodes.HOTEL, "skip", "not requested"));
            return skip;
        }
        Map<String, Object> updates = new LinkedHashMap<>();
        updates.put(TravelState.HOTELS, hotelAgentService.search(state));
        updates.putAll(TravelState.trace(TravelGraphNodes.HOTEL, "ok",
                state.retryCount() > 0 ? "cheaper hotel search" : "hotel search"));
        updates.putAll(TravelState.provenance(new ProvenanceEvent("hotels", "Tavily", "", 0.8, state.destination())));
        return updates;
    }
}
