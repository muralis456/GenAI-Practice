package com.example.travel.graph.node;

import com.example.travel.agent.HotelAgentService;
import com.example.travel.graph.TravelGraphNodes;
import com.example.travel.graph.TravelState;
import com.example.travel.model.ProvenanceEvent;
import com.example.travel.model.SearchHit;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
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
        HotelAgentService.HotelSearchResult result = hotelAgentService.search(state);
        Map<String, Object> updates = new LinkedHashMap<>();
        updates.put(TravelState.HOTELS, result.hotels());
        updates.putAll(TravelState.trace(TravelGraphNodes.HOTEL, "ok",
                state.hotelCheaper() ? "cheaper hotel search" : "hotel search"));
        List<ProvenanceEvent> events = new ArrayList<>();
        for (SearchHit hit : result.hits()) {
            events.add(new ProvenanceEvent("hotels", "Tavily",
                    hit.getUrl() == null ? "" : hit.getUrl(),
                    hit.getScore(),
                    hit.getTitle()));
        }
        if (events.isEmpty()) {
            events.add(new ProvenanceEvent("hotels", "Tavily", "", 0, state.destination()));
        }
        updates.put(TravelState.PROVENANCE, events);
        return updates;
    }
}
