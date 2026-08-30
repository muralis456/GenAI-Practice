package com.example.travel.service;

import com.example.travel.config.TravelModelsProperties.AgentRole;
import com.example.travel.graph.TravelState;
import com.example.travel.model.AgentDecision;
import com.example.travel.model.ReplanStrategy;
import com.example.travel.support.JsonSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ReplanAgentService {

    private static final Logger log = LoggerFactory.getLogger(ReplanAgentService.class);

    private final RoutedLlm routedLlm;
    private final JsonSupport jsonSupport;

    public ReplanAgentService(RoutedLlm routedLlm, JsonSupport jsonSupport) {
        this.routedLlm = routedLlm;
        this.jsonSupport = jsonSupport;
    }

    public Map<String, Object> decide(TravelState state) {
        ReplanStrategy strategy = askLlm(state);
        if (strategy.getActions() == null || strategy.getActions().isEmpty()) {
            strategy = fallback(state);
        }
        BigDecimal nextFactor = state.costFactor().multiply(BigDecimal.valueOf(0.82));
        if (strategy.hasAction("flight")) {
            nextFactor = nextFactor.multiply(BigDecimal.valueOf(0.95));
        }
        String notes = "Replan priority=" + strategy.getPriority()
                + " actions=" + strategy.getActions()
                + " cause=" + (TravelState.isBlank(strategy.getReason())
                ? String.join("; ", state.validationErrors()) : strategy.getReason());

        AgentDecision decision = new AgentDecision("replanner",
                TravelState.firstNonBlank(strategy.getPriority(), "REDUCE_COST").toUpperCase(),
                notes, 0.8);

        Map<String, Object> updates = new LinkedHashMap<>();
        updates.put(TravelState.RETRY_COUNT, state.retryCount() + 1);
        updates.put(TravelState.COST_FACTOR, nextFactor);
        updates.put(TravelState.TRAVEL_STYLE, "budget");
        updates.put(TravelState.REPLAN_NOTES, notes);
        updates.put(TravelState.REPLAN_STRATEGY, strategy);
        updates.put(TravelState.LAST_DECISION, decision);
        if (strategy.hasAction("attraction") && !state.attractions().isEmpty()) {
            updates.put(TravelState.ATTRACTIONS, new ArrayList<>(
                    state.attractions().subList(0, Math.min(3, state.attractions().size()))));
        }
        log.info("Replanner decision={} factor={}", decision.getDecision(), nextFactor);
        return updates;
    }

    private ReplanStrategy askLlm(TravelState state) {
        try {
            String content = routedLlm.complete(AgentRole.PLANNER,
                    "You are the Replanner Agent. Analyze why the plan failed and return JSON only: "
                            + "{\"reason\":\"budget_exceeded|invalid_itinerary|other\","
                            + "\"targetReduction\":0,\"actions\":[\"reduce_hotel_budget\"],"
                            + "\"priority\":\"hotel|flight|attractions\"}. "
                            + "Pick actions from: reduce_hotel_budget, cheaper_flight, remove_expensive_attractions.",
                    "Validation: " + state.validationErrors()
                            + "\nOverBudget=" + state.overBudget()
                            + "\nBudget=" + state.budgetSummary()
                            + "\nHotels=" + state.hotels().size()
                            + "\nFlights=" + state.flights().size()
                            + "\nUser=" + state.userRequest());
            return jsonSupport.read(content, ReplanStrategy.class).orElseGet(ReplanStrategy::new);
        } catch (Exception exception) {
            log.warn("Replanner LLM failed; using rule fallback", exception);
            return fallback(state);
        }
    }

    private ReplanStrategy fallback(TravelState state) {
        ReplanStrategy strategy = new ReplanStrategy();
        strategy.setReason(state.overBudget() ? "budget_exceeded" : "validation_failed");
        strategy.setPriority(state.overBudget() ? "hotel" : "attractions");
        strategy.setActions(new ArrayList<>(List.of(
                "reduce_hotel_budget", "prefer_cheaper_options", "remove_expensive_attractions")));
        if (state.budgetSummary() != null && state.budgetSummary().getEstimatedCost() != null
                && state.budget() != null) {
            strategy.setTargetReduction(state.budgetSummary().getEstimatedCost()
                    .subtract(state.budget()).doubleValue());
        }
        return strategy;
    }
}
