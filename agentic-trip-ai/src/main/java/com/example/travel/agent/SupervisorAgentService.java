package com.example.travel.agent;

import com.example.travel.config.TravelModelsProperties.AgentRole;
import com.example.travel.graph.GraphExecutionLogger;
import com.example.travel.graph.NodeFailureRouting;
import com.example.travel.graph.TravelGraphNodes;
import com.example.travel.graph.TravelState;
import com.example.travel.model.AgentDecision;
import com.example.travel.model.ReplanAction;
import com.example.travel.model.ReplanStrategy;
import com.example.travel.model.SupervisorAssessment;
import com.example.travel.service.RoutedLlm;
import com.example.travel.support.JsonSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Hybrid supervisor: deterministic safety rules plus LLM quality assessment.
 */
@Service
public class SupervisorAgentService {

    private static final Logger log = LoggerFactory.getLogger(SupervisorAgentService.class);

    private final RoutedLlm routedLlm;
    private final JsonSupport jsonSupport;

    public SupervisorAgentService(RoutedLlm routedLlm, JsonSupport jsonSupport) {
        this.routedLlm = routedLlm;
        this.jsonSupport = jsonSupport;
    }

    public Map<String, Object> review(TravelState state) {
        SupervisorAssessment assessment = llmAssess(state);
        String decision = decide(state, assessment);

        AgentDecision recorded = new AgentDecision("supervisor", decision,
                assessment.getReason().isBlank()
                        ? buildDeterministicReason(state)
                        : assessment.getReason(),
                assessment.getQualityHint());

        Map<String, Object> updates = new LinkedHashMap<>();
        updates.put(TravelState.LAST_DECISION, recorded);
        updates.put(TravelState.SUPERVISOR_DECISION, decision);
        updates.put(TravelState.SUPERVISOR_ASSESSMENT, assessment);
        if (assessment.getRecommendedAction() != null && decision.equals(TravelGraphNodes.ROUTE_RETRY)) {
            ReplanStrategy strategy = new ReplanStrategy();
            strategy.setActions(List.of(assessment.getRecommendedAction().wireName()));
            strategy.setReason(assessment.getReason());
            updates.put(TravelState.REPLAN_STRATEGY, strategy);
        }
        GraphExecutionLogger.supervisorDecision(state, decision, assessment.getQualityHint(), recorded.getReason());
        return updates;
    }

    public String decide(TravelState state) {
        return decide(state, state.supervisorAssessment());
    }

    private String decide(TravelState state, SupervisorAssessment assessment) {
        if (state.retryCount() >= state.maxRetries()) {
            return TravelGraphNodes.ROUTE_PROCEED;
        }
        if (NodeFailureRouting.hasRetryableFailure(state)) {
            log.info("Supervisor retrying after node failure on {}", state.nodeFailure().getLastFailedNode());
            return TravelGraphNodes.ROUTE_RETRY;
        }
        if (state.needsFlights() && flightsUnusable(state)) {
            return TravelGraphNodes.ROUTE_RETRY;
        }
        if (state.needsHotels() && state.hotels().isEmpty()) {
            return TravelGraphNodes.ROUTE_RETRY;
        }
        if (state.needsResearch() && state.research().isEmpty() && state.attractions().isEmpty()) {
            return TravelGraphNodes.ROUTE_RETRY;
        }
        if (state.shouldReplanForBudget()) {
            assessment.setDecision("REPLAN");
            assessment.setReason("Hotel and flight costs exceed budget ceiling");
            assessment.setRecommendedAction(ReplanAction.REDUCE_HOTEL_BUDGET);
            return TravelGraphNodes.ROUTE_RETRY;
        }

        String llmDecision = assessment.getDecision() == null ? "" : assessment.getDecision().toUpperCase(Locale.ROOT);
        if (("RETRY".equals(llmDecision) || "REPLAN".equals(llmDecision))
                && (assessment.getQualityHint() < 0.70 || assessment.getConfidence() >= 0.75)) {
            log.info("Supervisor LLM decision {} hint={} confidence={}",
                    llmDecision, assessment.getQualityHint(), assessment.getConfidence());
            return TravelGraphNodes.ROUTE_RETRY;
        }
        if (TravelGraphNodes.ROUTE_PROCEED.equalsIgnoreCase(llmDecision)
                && assessment.getQualityHint() < 0.65
                && state.retryCount() < state.maxRetries()) {
            return TravelGraphNodes.ROUTE_RETRY;
        }
        return TravelGraphNodes.ROUTE_PROCEED;
    }

    private SupervisorAssessment llmAssess(TravelState state) {
        SupervisorAssessment fallback = new SupervisorAssessment();
        fallback.setDecision(TravelGraphNodes.ROUTE_PROCEED);
        fallback.setQualityHint(0.85);
        fallback.setConfidence(0.75);
        try {
            String content = routedLlm.complete(AgentRole.EXTRACT,
                    "You are the travel plan supervisor. Return JSON only: "
                            + "{\"decision\":\"PROCEED|RETRY|REPLAN\",\"reason\":\"\","
                            + "\"qualityHint\":0.0,\"confidence\":0.0,"
                            + "\"suggestedStrategy\":\"reduce_hotel_budget|cheaper_flight|adjust_itinerary|\"}. "
                            + "Judge flight/hotel availability, budget fit, weather, itinerary, and preferences.",
                    "User request: " + state.userRequest()
                            + "\nDestination: " + state.destination()
                            + "\nFlights: " + state.flights().size()
                            + "\nHotels: " + state.hotels().size()
                            + "\nResearch topics: " + state.research().size()
                            + "\nBudget within ceiling: " + (state.budgetSummary() == null || state.budgetSummary().isWithinBudget())
                            + "\nWeather: " + (state.weather() == null ? "n/a" : state.weather().toDisplay())
                            + "\nItinerary days: " + (state.itinerary() == null ? 0 : state.itinerary().getDays().size()));
            return jsonSupport.readTree(content).map(this::parseAssessment).orElse(fallback);
        } catch (Exception ex) {
            log.debug("Supervisor LLM assessment skipped: {}", ex.getMessage());
            return fallback;
        }
    }

    private SupervisorAssessment parseAssessment(JsonNode tree) {
        SupervisorAssessment assessment = new SupervisorAssessment();
        if (tree.hasNonNull("decision")) {
            assessment.setDecision(tree.get("decision").asString());
        }
        if (tree.hasNonNull("reason")) {
            assessment.setReason(tree.get("reason").asString());
        }
        if (tree.has("qualityHint")) {
            assessment.setQualityHint(tree.get("qualityHint").asDouble());
        }
        if (tree.has("confidence")) {
            assessment.setConfidence(tree.get("confidence").asDouble());
        }
        if (tree.hasNonNull("suggestedStrategy")) {
            assessment.setSuggestedStrategy(tree.get("suggestedStrategy").asString());
            ReplanAction.fromToken(assessment.getSuggestedStrategy()).ifPresent(assessment::setRecommendedAction);
        }
        return assessment;
    }

    private String buildDeterministicReason(TravelState state) {
        return "flights=" + state.needsFlights()
                + " hotels=" + state.needsHotels()
                + " research=" + state.needsResearch();
    }

    private boolean flightsUnusable(TravelState state) {
        if (state.flights().isEmpty()) {
            return true;
        }
        return state.flights().stream().allMatch(flight ->
                flight == null || "unavailable".equalsIgnoreCase(flight.getStatus()));
    }
}
