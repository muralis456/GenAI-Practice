package com.example.travel.graph.node;

import com.example.travel.graph.GraphExecutionLogger;
import com.example.travel.graph.NodeFailureSupport;
import com.example.travel.graph.TravelGraphNodes;
import com.example.travel.graph.TravelState;
import com.example.travel.rag.AgenticRagService;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class RagNode implements NodeAction<TravelState> {

    private final AgenticRagService agenticRagService;
    public RagNode(AgenticRagService agenticRagService) {
        this.agenticRagService = agenticRagService;
    }

    @Override
    public Map<String, Object> apply(TravelState state) {
        try {
            AgenticRagService.RagResult result = agenticRagService.run(state);
            Map<String, Object> updates = new LinkedHashMap<>();
            updates.put(TravelState.RAG_ENABLED, Boolean.TRUE);
            updates.put(TravelState.RAG_DECISION, result.decision());
            updates.put(TravelState.RAG_QUERY, result.query());
            updates.put(TravelState.RAG_CONTEXT, result.context());
            updates.put(TravelState.RAG_SOURCES, result.sources());
            updates.put(TravelState.RAG_ITERATIONS, result.iterations());
            updates.put(TravelState.RAG_SUFFICIENT, result.sufficient());
            updates.put(TravelState.RAG_RETRIEVAL_METHOD, result.retrievalMethod());
            updates.put(TravelState.RAG_CANDIDATE_COUNT, result.candidateCount());
            updates.put(TravelState.RAG_RERANKED_COUNT, result.rerankedCount());
            updates.put(TravelState.RAG_CONTEXT_CHARS, result.contextChars());
            updates.put(TravelState.RAG_DESTINATION, result.destination());
            updates.put(TravelState.RAG_COUNTRY, result.country());
            updates.put(TravelState.RAG_TOPICS, result.topics());
            updates.put(TravelState.RAG_EVIDENCE_SCORE, result.evidenceScore());
            updates.putAll(TravelState.trace(TravelGraphNodes.RAG, "ok", result.decision()));
            GraphExecutionLogger.specialistResult(TravelGraphNodes.RAG, state, "ok",
                    "decision=" + result.decision() + " method=" + result.retrievalMethod() + " candidates=" + result.candidateCount() + " reranked=" + result.rerankedCount() + " iterations=" + result.iterations() + " sources=" + result.sources().size());
            org.slf4j.LoggerFactory.getLogger(RagNode.class).info(
                    "RAG node result query={} decision={} sufficient={} candidates={} reranked={} contextChars={} evidenceScore={} sources={} destination={} country={} topics={}",
                    result.query(), result.decision(), result.sufficient(), result.candidateCount(),
                    result.rerankedCount(), result.contextChars(), result.evidenceScore(), result.sources(),
                    result.destination(), result.country(), result.topics());
            return updates;
        } catch (Exception ex) {
            return NodeFailureSupport.record(TravelGraphNodes.RAG, state, ex, true,
                    state.nodeFailure().getNodeRetryCount());
        }
    }
}
