package com.example.travel.graph.node;

import com.example.travel.graph.TravelGraphNodes;
import com.example.travel.graph.TravelState;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class ReplanNode implements NodeAction<TravelState> {

    @Override
    public Map<String, Object> apply(TravelState state) {
        BigDecimal nextFactor = state.costFactor().multiply(BigDecimal.valueOf(0.82));
        Map<String, Object> updates = new LinkedHashMap<>();
        updates.put(TravelState.RETRY_COUNT, state.retryCount() + 1);
        updates.put(TravelState.COST_FACTOR, nextFactor);
        updates.put(TravelState.TRAVEL_STYLE, "budget");
        // Cost/style guidance only — do not dump raw validation text into search queries.
        String reason = state.validationErrors().isEmpty()
                ? "over budget"
                : String.join("; ", state.validationErrors());
        updates.put(TravelState.REPLAN_NOTES,
                "Reduce hotel cost; prefer cheaper options; drop expensive attractions. Cause: " + reason);
        updates.putAll(TravelState.trace(TravelGraphNodes.REPLAN, "ok",
                "retry " + (state.retryCount() + 1) + " costFactor=" + nextFactor));
        return updates;
    }
}
