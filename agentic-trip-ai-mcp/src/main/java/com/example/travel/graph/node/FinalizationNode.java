package com.example.travel.graph.node;

import com.example.travel.agent.FinalPlannerAgentService;
import com.example.travel.graph.TravelGraphNodes;
import com.example.travel.graph.TravelState;
import com.example.travel.rag.RagAnswerService;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Finalization node.
 *
 * For knowledge-only requests, converts grounded RAG context into a natural-language
 * answer. Raw retrieved context is never exposed directly to the user.
 */
@Component
public class FinalizationNode implements NodeAction<TravelState> {

    private final FinalPlannerAgentService finalPlannerAgentService;
    private final RagAnswerService ragAnswerService;

    public FinalizationNode(FinalPlannerAgentService finalPlannerAgentService,
                            RagAnswerService ragAnswerService) {
        this.finalPlannerAgentService = finalPlannerAgentService;
        this.ragAnswerService = ragAnswerService;
    }

    @Override
    public Map<String, Object> apply(TravelState state) {
        boolean knowledgeOnly = isKnowledgeOnly(state);
        String tips = knowledgeOnly
                ? ragAnswerService.answer(state)
                : finalPlannerAgentService.buildTips(state);

        Map<String, Object> updates = new LinkedHashMap<>();
        // A knowledge answer does not require HITL approval.
        updates.put(TravelState.AWAITING_APPROVAL, !knowledgeOnly);
        updates.put(TravelState.FINAL_TIPS, tips);
        updates.putAll(TravelState.trace(TravelGraphNodes.FINAL, "ok",
                knowledgeOnly ? "grounded RAG answer generated" : "tips generated"));
        return updates;
    }

    private boolean isKnowledgeOnly(TravelState state) {
        return state.needsKnowledge()
                && !state.needsFlights()
                && !state.needsHotels()
                && !state.needsResearch()
                && !state.needsWeather()
                && !state.needsBudget()
                && !state.needsItinerary();
    }
}
