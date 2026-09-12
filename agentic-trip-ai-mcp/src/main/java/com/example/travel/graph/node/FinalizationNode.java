package com.example.travel.graph.node;

import com.example.travel.agent.FinalPlannerAgentService;
import com.example.travel.graph.TravelGraphNodes;
import com.example.travel.graph.TravelState;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Finalization node: generates travel tips only. The domain plan is assembled by {@link com.example.travel.service.TripPlanAssembler}.
 */
@Component
public class FinalizationNode implements NodeAction<TravelState> {

    private final FinalPlannerAgentService finalPlannerAgentService;

    public FinalizationNode(FinalPlannerAgentService finalPlannerAgentService) {
        this.finalPlannerAgentService = finalPlannerAgentService;
    }

    @Override
    public Map<String, Object> apply(TravelState state) {
        String tips;
        boolean knowledgeOnlyResponse = state.ragSufficient()
                && !state.ragAnswer().isBlank()
                && !state.needsFlights()
                && !state.needsHotels()
                && !state.needsWeather()
                && !state.needsBudget()
                && !state.needsItinerary();
        if (knowledgeOnlyResponse) {
            tips = state.ragAnswer();
        } else {
            tips = finalPlannerAgentService.buildTips(state);
            if ((tips == null || tips.isBlank()) && !state.ragAnswer().isBlank()) {
                tips = state.ragAnswer();
            }
        }
        Map<String, Object> updates = new LinkedHashMap<>();
        updates.put(TravelState.AWAITING_APPROVAL, Boolean.TRUE);
        updates.put(TravelState.FINAL_TIPS, tips);
        updates.putAll(TravelState.trace(TravelGraphNodes.FINAL, "ok",
                state.validationErrors().isEmpty()
                        ? "tips generated"
                        : "tips generated (with caveats)"));
        return updates;
    }
}
