package com.example.travel.agent;

import com.example.travel.config.TravelModelsProperties.AgentRole;
import com.example.travel.graph.TravelState;
import com.example.travel.model.AgentDecision;
import com.example.travel.model.IntentPlan;
import com.example.travel.service.RoutedLlm;
import com.example.travel.support.IntentClassifier;
import com.example.travel.support.TripRequirementsParser;
import com.example.travel.support.JsonSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class IntentAgentService {

    private static final Logger log = LoggerFactory.getLogger(IntentAgentService.class);

    private final RoutedLlm routedLlm;
    private final JsonSupport jsonSupport;

    public IntentAgentService(RoutedLlm routedLlm, JsonSupport jsonSupport) {
        this.routedLlm = routedLlm;
        this.jsonSupport = jsonSupport;
    }

    public Map<String, Object> classify(TravelState state) {
        IntentPlan plan = IntentClassifier.classify(state.userRequest());
        if (plan.getConfidence() < IntentClassifier.LLM_THRESHOLD) {
            plan = refineWithLlm(state, plan);
        }
        log.info("Intent agent requestType={} confidence={} for query={}",
                plan.getRequestType(), plan.getConfidence(), state.userRequest());
        AgentDecision decision = new AgentDecision("intent", plan.getRequestType(), plan.summary(), plan.getConfidence());
        Map<String, Object> updates = new LinkedHashMap<>();
        updates.put(TravelState.REQUEST_TYPE, plan.getRequestType());
        TravelState.applyIntentAndRun(updates, plan);
        updates.put(TravelState.PLAN_STRATEGY, plan.getStrategy());
        updates.put(TravelState.PLAN_PRIORITY, plan.getPriority());
        updates.put(TravelState.INTENT_CONFIDENCE, plan.getConfidence());
        updates.put(TravelState.TRIP_REQUIREMENTS, TripRequirementsParser.parse(state.userRequest()));
        updates.put(TravelState.LAST_DECISION, decision);
        return updates;
    }

    private IntentPlan refineWithLlm(TravelState state, IntentPlan fallback) {
        try {
            String content = routedLlm.complete(AgentRole.EXTRACT,
                    "You are the Intent Agent. Return JSON only: "
                            + "{\"requestType\":\"\",\"needsFlights\":false,\"needsHotels\":false,"
                            + "\"needsResearch\":false,\"needsWeather\":false,\"needsBudget\":false,"
                            + "\"needsItinerary\":false,\"strategy\":\"\",\"priority\":\"\",\"confidence\":0.0}. "
                            + "If the user already booked flights, needsFlights must be false.",
                    "User request: " + state.userRequest()
                            + "\nHeuristic guess: " + fallback.summary());
            return jsonSupport.read(content, IntentPlan.class).map(parsed -> {
                if (parsed.getConfidence() <= 0) {
                    parsed.setConfidence(0.8);
                }
                return mergeTripScope(fallback, parsed);
            }).orElse(fallback);
        } catch (Exception exception) {
            log.warn("Intent LLM fallback failed; using deterministic plan", exception);
            return fallback;
        }
    }

    private IntentPlan mergeTripScope(IntentPlan base, IntentPlan llm) {
        if (IntentPlan.TRIP_PLANNING.equals(base.getRequestType())) {
            llm.setRequestType(IntentPlan.TRIP_PLANNING);
            llm.setNeedsFlights(true);
            llm.setNeedsHotels(true);
            llm.setNeedsResearch(true);
            llm.setNeedsWeather(true);
            llm.setNeedsBudget(true);
            llm.setNeedsItinerary(true);
            return llm;
        }
        llm.setNeedsFlights(base.isNeedsFlights() || llm.isNeedsFlights());
        llm.setNeedsHotels(base.isNeedsHotels() || llm.isNeedsHotels());
        llm.setNeedsResearch(base.isNeedsResearch() || llm.isNeedsResearch());
        llm.setNeedsWeather(base.isNeedsWeather() || llm.isNeedsWeather());
        llm.setNeedsBudget(base.isNeedsBudget() || llm.isNeedsBudget());
        llm.setNeedsItinerary(base.isNeedsItinerary() || llm.isNeedsItinerary());
        return llm;
    }
}
