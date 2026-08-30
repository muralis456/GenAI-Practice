package com.example.travel.agent;

import com.example.travel.model.AgentDecision;
import com.example.travel.graph.TravelGraphNodes;
import com.example.travel.graph.TravelState;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Graph supervisor: are specialist results enough to continue, or should we replan?
 */
@Service
public class SupervisorAgentService {

    public Map<String, Object> review(TravelState state) {
        String decision = decide(state);
        AgentDecision recorded = new AgentDecision("supervisor", decision,
                "flights=" + state.needsFlights() + " hotels=" + state.needsHotels()
                        + " research=" + state.needsResearch() + " weather=" + state.needsWeather(),
                1.0);
        Map<String, Object> updates = new LinkedHashMap<>();
        updates.put(TravelState.LAST_DECISION, recorded);
        updates.put(TravelState.SUPERVISOR_DECISION, decision);
        return updates;
    }

    public String nextNode(TravelState state) {
        return TravelGraphNodes.ROUTE_RETRY.equals(decide(state))
                ? TravelGraphNodes.REPLAN
                : TravelGraphNodes.BUDGET;
    }

    public String decide(TravelState state) {
        if (state.retryCount() >= state.maxRetries()) {
            return TravelGraphNodes.ROUTE_PROCEED;
        }
        if (state.needsFlights() && flightsUnusable(state)) {
            return TravelGraphNodes.ROUTE_RETRY;
        }
        if (state.needsHotels() && state.hotels().isEmpty()) {
            return TravelGraphNodes.ROUTE_RETRY;
        }
        return TravelGraphNodes.ROUTE_PROCEED;
    }

    private boolean flightsUnusable(TravelState state) {
        if (state.flights().isEmpty()) {
            return true;
        }
        return state.flights().stream().allMatch(flight ->
                flight == null || "unavailable".equalsIgnoreCase(flight.getStatus()));
    }
}
