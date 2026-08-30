package com.example.travel.agent;

import com.example.travel.graph.TravelState;
import com.example.travel.model.AgentDecision;
import com.example.travel.model.IntentPlan;
import com.example.travel.support.IntentClassifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class IntentAgentService {

    private static final Logger log = LoggerFactory.getLogger(IntentAgentService.class);

    public Map<String, Object> classify(TravelState state) {
        IntentPlan plan = IntentClassifier.classify(state.userRequest());
        log.info("Intent agent requestType={} for query={}", plan.getRequestType(), state.userRequest());
        AgentDecision decision = new AgentDecision("intent", plan.getRequestType(), plan.summary(), 0.95);
        Map<String, Object> updates = new LinkedHashMap<>();
        updates.put(TravelState.REQUEST_TYPE, plan.getRequestType());
        updates.put(TravelState.NEEDS_FLIGHTS, plan.isNeedsFlights());
        updates.put(TravelState.NEEDS_HOTELS, plan.isNeedsHotels());
        updates.put(TravelState.NEEDS_RESEARCH, plan.isNeedsResearch());
        updates.put(TravelState.NEEDS_WEATHER, plan.isNeedsWeather());
        updates.put(TravelState.NEEDS_BUDGET, plan.isNeedsBudget());
        updates.put(TravelState.NEEDS_ITINERARY, plan.isNeedsItinerary());
        updates.put(TravelState.PLAN_STRATEGY, plan.getStrategy());
        updates.put(TravelState.PLAN_PRIORITY, plan.getPriority());
        updates.put(TravelState.LAST_DECISION, decision);
        return updates;
    }
}
