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
        boolean requiresApproval = state.needsItinerary()
                || ("TRIP_PLANNING".equalsIgnoreCase(state.requestType())
                    && (state.needsFlights() || state.needsHotels() || state.needsResearch()
                        || state.needsWeather() || state.needsBudget() || state.needsKnowledge()));

        String tips = "";
        if ("HISTORY".equalsIgnoreCase(state.requestType())) {
            // HistoryNode may already have produced a safe no-result message.
            // Never overwrite it with an empty finalization payload.
            tips = state.finalTips();
        } else if (requiresApproval) {
            // Keep durable RAG knowledge separate from generated trip tips.
            // The API exposes RAG guidance as plan.knowledge so the UI can
            // label it clearly and show its provenance without mixing it into
            // the generic Travel Notes section.
            tips = finalPlannerAgentService.buildTips(state);
        }

        Map<String, Object> updates = new LinkedHashMap<>();
        updates.put(TravelState.AWAITING_APPROVAL, requiresApproval);
        updates.put(TravelState.FINAL_TIPS, tips == null ? "" : tips);
        updates.putAll(TravelState.trace(TravelGraphNodes.FINAL, "ok",
                state.validationErrors().isEmpty()
                        ? (requiresApproval ? "trip plan ready for approval" : "informational response complete")
                        : (requiresApproval ? "trip plan ready with caveats" : "informational response complete with caveats")));
        return updates;
    }
}
