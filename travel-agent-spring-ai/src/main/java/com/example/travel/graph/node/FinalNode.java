package com.example.travel.graph.node;

import com.example.travel.graph.TravelGraphNodes;
import com.example.travel.graph.TravelState;
import com.example.travel.agent.FinalPlannerAgentService;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Runs only after Validator routes VALID (or retries exhausted).
 * Composes the user-facing final report via the Final LLM agent — matching
 * Validator → Final Agent → USER in the multi-agent design.
 */
@Component
public class FinalNode implements NodeAction<TravelState> {

    private final FinalPlannerAgentService finalPlannerAgentService;

    public FinalNode(FinalPlannerAgentService finalPlannerAgentService) {
        this.finalPlannerAgentService = finalPlannerAgentService;
    }

    @Override
    public Map<String, Object> apply(TravelState state) {
        String report = finalPlannerAgentService.compose(state);
        String withHitl = report
                + "\n\n---\nValidated by Validator Agent"
                + (state.validationErrors().isEmpty()
                ? " (passed)."
                : " with caveats: " + String.join("; ", state.validationErrors()) + ".")
                + (state.semanticNotes().isEmpty() ? "" : " Semantic: " + String.join("; ", state.semanticNotes()) + ".")
                + "\nApprove to confirm, Modify to change the plan, or Reject to cancel.";

        Map<String, Object> updates = new LinkedHashMap<>();
        updates.put(TravelState.AWAITING_APPROVAL, Boolean.TRUE);
        updates.put(TravelState.FINAL_PLAN, withHitl);
        updates.putAll(TravelState.trace(TravelGraphNodes.FINAL, "ok",
                state.validationErrors().isEmpty()
                        ? "final LLM report (validated)"
                        : "final LLM report (with caveats)"));
        return updates;
    }
}
