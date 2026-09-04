package com.example.travel.graph;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * Structured logs for LangGraph orchestration, routing, quality, and replanning.
 */
public final class GraphExecutionLogger {

    private static final Logger log = LoggerFactory.getLogger(GraphExecutionLogger.class);

    private GraphExecutionLogger() {
    }

    public static void nodeStart(String node, TravelState state) {
        log.info("[graph] node={} phase=start threadId={} retry={}/{} needs=[flight={},hotel={},research={},weather={},budget={},itinerary={}]",
                node,
                threadId(state),
                state.retryCount(),
                state.maxRetries(),
                state.needsFlights(),
                state.needsHotels(),
                state.needsResearch(),
                state.needsWeather(),
                state.needsBudget(),
                state.needsItinerary());
    }

    public static void nodeComplete(String node, TravelState state, long durationMs) {
        log.info("[graph] node={} phase=complete threadId={} durationMs={}",
                node, threadId(state), durationMs);
    }

    public static void nodeFailed(String node, TravelState state, long durationMs, String error) {
        log.warn("[graph] node={} phase=failed threadId={} durationMs={} error={}",
                node, threadId(state), durationMs, error);
    }

    public static void route(String from, String to, TravelState state, String reason) {
        log.info("[graph] route from={} to={} threadId={} reason={} specialists={}",
                from, to, threadId(state), reason, SpecialistRouter.plannedSpecialists(state));
    }

    public static void parallelFanOut(TravelState state) {
        log.info("[graph] parallel-fan-out threadId={} specialists={}",
                threadId(state), SpecialistRouter.plannedSpecialists(state));
    }

    public static void specialistResult(String node, TravelState state, String status, String summary) {
        log.info("[graph] specialist={} threadId={} status={} {}",
                node, threadId(state), status, summary);
    }

    public static void nodeFailure(String node, TravelState state, String error, boolean retryable, int attempt) {
        log.warn("[graph] node-failure node={} threadId={} retryable={} attempt={} error={}",
                node, threadId(state), retryable, attempt, error);
    }

    public static void supervisorDecision(TravelState state, String decision, double qualityHint, String reason) {
        log.info("[graph] supervisor threadId={} decision={} qualityHint={} flights={} hotels={} research={} reason={}",
                threadId(state),
                decision,
                qualityHint,
                state.flights().size(),
                state.hotels().size(),
                state.research().size(),
                reason);
    }

    public static void validation(TravelState state, double overall, boolean pass, List<String> errors, List<String> semantic) {
        log.info("[graph] validator threadId={} overall={} pass={} errors={} semantic={}",
                threadId(state),
                String.format("%.2f", overall),
                pass,
                errors.size(),
                semantic.size());
        if (!errors.isEmpty()) {
            log.info("[graph] validator-errors threadId={} {}", threadId(state), errors);
        }
        if (!semantic.isEmpty()) {
            log.info("[graph] validator-semantic threadId={} {}", threadId(state), semantic);
        }
    }

    public static void semanticValidation(TravelState state, String status, double score, List<String> issues) {
        log.info("[graph] semantic threadId={} status={} score={} issues={}",
                threadId(state), status, String.format("%.2f", score), issues.size());
    }

    public static void replan(TravelState state, List<?> actions, Map<String, Object> selectiveNeeds) {
        log.info("[graph] replan threadId={} retry={}/{} actions={} selective={}",
                threadId(state),
                state.retryCount() + 1,
                state.maxRetries(),
                actions,
                selectiveNeeds);
    }

    public static void hitl(TravelState state, String decision, boolean awaiting) {
        log.info("[graph] hitl threadId={} decision={} awaitingApproval={}",
                threadId(state), decision, awaiting);
    }

    public static void streamTransition(String threadId, String node, boolean end) {
        log.info("[graph] stream threadId={} node={} end={}", threadId, node, end);
    }

    public static void runContext(String phase, String threadId, String policy) {
        log.info("[graph] context phase={} threadId={} policy={}", phase, threadId, policy);
    }

    private static String threadId(TravelState state) {
        return TravelState.isBlank(state.graphThreadId()) ? "-" : state.graphThreadId();
    }
}
