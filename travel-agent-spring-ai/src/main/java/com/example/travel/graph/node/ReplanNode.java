package com.example.travel.graph.node;

import com.example.travel.graph.TravelGraphNodes;
import com.example.travel.graph.TravelState;
import com.example.travel.service.ReplanAgentService;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class ReplanNode implements NodeAction<TravelState> {

    private final ReplanAgentService replanAgentService;

    public ReplanNode(ReplanAgentService replanAgentService) {
        this.replanAgentService = replanAgentService;
    }

    @Override
    public Map<String, Object> apply(TravelState state) {
        Map<String, Object> updates = new LinkedHashMap<>(replanAgentService.decide(state));
        updates.putAll(TravelState.trace(TravelGraphNodes.REPLAN, "ok",
                String.valueOf(updates.get(TravelState.REPLAN_NOTES))));
        return updates;
    }
}
