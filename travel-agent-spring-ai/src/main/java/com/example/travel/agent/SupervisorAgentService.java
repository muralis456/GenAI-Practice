package com.example.travel.agent;

import com.example.travel.config.TravelModelsProperties.AgentRole;
import com.example.travel.graph.NodeFailureRouting;
import com.example.travel.graph.TravelGraphNodes;
import com.example.travel.graph.TravelState;
import com.example.travel.model.AgentDecision;
import com.example.travel.model.SupervisorAssessment;
import com.example.travel.service.RoutedLlm;
import com.example.travel.support.JsonSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
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
        String decision = decide(state);
        SupervisorAssessment assessment = llmAssess(state);
        if (TravelGraphNodes.ROUTE_PROCEED.equals(decision)
                && assessment.getQualityHint() < 0.65
                && state.retryCount() < state.maxRetries()) {
            decision = TravelGraphNodes.ROUTE_RETRY;
            log.info("Supervisor LLM quality hint {} triggered retry", assessment.getQualityHint());
        }

        AgentDecision recorded = new AgentDecision("supervisor", decision,
                assessment.getReason().isBlank()
                        ? "flights=" + state.needsFlights() + " hotels=" + state.needsHotels()
                        : assessment.getReason(),
                assessment.getQualityHint());

        Map<String, Object> updates = new LinkedHashMap<>();
        updates.put(TravelState.LAST_DECISION, recorded);
        updates.put(TravelState.SUPERVISOR_DECISION, decision);
        updates.put(TravelState.SUPERVISOR_ASSESSMENT, assessment);
        return updates;
    }

    public String decide(TravelState state) {
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
        return TravelGraphNodes.ROUTE_PROCEED;
    }

    private SupervisorAssessment llmAssess(TravelState state) {
        SupervisorAssessment fallback = new SupervisorAssessment();
        fallback.setDecision(TravelGraphNodes.ROUTE_PROCEED);
        fallback.setQualityHint(0.85);
        try {
            String content = routedLlm.complete(AgentRole.EXTRACT,
                    "You are the travel plan supervisor. Return JSON only: "
                            + "{\"decision\":\"PROCEED|RETRY|REPLAN\",\"reason\":\"\","
                            + "\"qualityHint\":0.0,\"suggestedStrategy\":\"reduce_hotel_budget|cheaper_flight|adjust_itinerary|\"}. "
                            + "Judge itinerary balance, preference fit, weather conflicts, and data completeness.",
                    "User request: " + state.userRequest()
                            + "\nDestination: " + state.destination()
                            + "\nFlights: " + state.flights().size()
                            + "\nHotels: " + state.hotels().size()
                            + "\nResearch topics: " + state.research().size()
                            + "\nWeather: " + (state.weather() == null ? "n/a" : state.weather().toDisplay())
                            + "\nItinerary days: " + (state.itinerary() == null ? 0 : state.itinerary().getDays().size()));
            return jsonSupport.read(content, SupervisorAssessment.class).orElse(fallback);
        } catch (Exception ex) {
            log.debug("Supervisor LLM assessment skipped: {}", ex.getMessage());
            return fallback;
        }
    }

    private boolean flightsUnusable(TravelState state) {
        if (state.flights().isEmpty()) {
            return true;
        }
        return state.flights().stream().allMatch(flight ->
                flight == null || "unavailable".equalsIgnoreCase(flight.getStatus()));
    }
}
