package com.example.travel.graph.node;

import com.example.travel.graph.TravelGraphNodes;
import com.example.travel.graph.TravelState;
import com.example.travel.service.HotelAgentService;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class HotelNode implements NodeAction<TravelState> {

    private final HotelAgentService hotelAgentService;

    public HotelNode(HotelAgentService hotelAgentService) {
        this.hotelAgentService = hotelAgentService;
    }

    @Override
    public Map<String, Object> apply(TravelState state) {
        Map<String, Object> updates = new LinkedHashMap<>();
        updates.put(TravelState.HOTELS, hotelAgentService.search(state));
        updates.putAll(TravelState.trace(TravelGraphNodes.HOTEL, "ok",
                state.retryCount() > 0 ? "cheaper hotel search" : "hotel search"));
        return updates;
    }
}
