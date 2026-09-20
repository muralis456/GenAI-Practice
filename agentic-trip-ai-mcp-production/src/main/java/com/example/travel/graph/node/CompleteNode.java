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
        if (state == null
                || state.goalEvaluation() == null
                || state.goalEvaluation().getStatus() != com.example.travel.model.GoalEvaluation.Status.ACHIEVED
                || !"approve".equalsIgnoreCase(state.hitlDecision())) {
            throw new IllegalStateException("Cannot complete a trip plan before the goal is achieved and explicitly approved.");
        }
        Map<String, Object> updates = new LinkedHashMap<>();
        updates.put(TravelState.AWAITING_APPROVAL, Boolean.FALSE);
        updates.putAll(TravelState.trace(TravelGraphNodes.COMPLETE, "ok", "plan approved"));
        return updates;
    }
}
