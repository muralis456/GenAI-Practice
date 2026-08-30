package com.example.travel.graph.node;

import com.example.travel.graph.TravelGraphNodes;
import com.example.travel.graph.TravelState;
import com.example.travel.service.IntentAgentService;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class IntentNode implements NodeAction<TravelState> {

    private final IntentAgentService intentAgentService;

    public IntentNode(IntentAgentService intentAgentService) {
        this.intentAgentService = intentAgentService;
    }

    @Override
    public Map<String, Object> apply(TravelState state) {
        Map<String, Object> updates = new LinkedHashMap<>(intentAgentService.classify(state));
        updates.putAll(TravelState.trace(TravelGraphNodes.INTENT, "ok",
                String.valueOf(updates.get(TravelState.REQUEST_TYPE))));
        return updates;
    }
}
