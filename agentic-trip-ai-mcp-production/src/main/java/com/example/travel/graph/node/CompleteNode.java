package com.example.travel.graph.node;

import com.example.travel.graph.TravelGraphNodes;
import com.example.travel.graph.TravelState;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Marks the trip plan as human-approved and closes the HITL wait.
 */
@Component
public class CompleteNode implements NodeAction<TravelState> {

    @Override
    public Map<String, Object> apply(TravelState state) {
        Map<String, Object> updates = new LinkedHashMap<>();
        updates.put(TravelState.AWAITING_APPROVAL, Boolean.FALSE);
        updates.put(TravelState.HITL_DECISION, "approve");
        updates.putAll(TravelState.trace(TravelGraphNodes.COMPLETE, "ok", "plan approved"));
        return updates;
    }
}
