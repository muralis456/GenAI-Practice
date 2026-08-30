package com.example.travel.agent;

import com.example.travel.graph.TravelState;
import com.example.travel.model.AgentDecision;
import com.example.travel.model.ReplanStrategy;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Applies {@link ReplanStrategy} actions to graph state. The LLM chooses actions;
 * this executor is the only place cost/hotel/flight knobs are changed.
 */
@Component
public class ReplanStrategyExecutor {

    public Map<String, Object> apply(TravelState state, ReplanStrategy strategy) {
        Map<String, Object> updates = new LinkedHashMap<>();
        BigDecimal factor = state.costFactor();
        boolean hotelCheaper = state.hotelCheaper();
        String flightPreference = state.flightPreference();
        String travelStyle = state.travelStyle();

        if (strategy.hasAction("reduce_hotel_budget")) {
            hotelCheaper = true;
            factor = factor.multiply(BigDecimal.valueOf(0.90));
        }
        if (strategy.hasAction("cheaper_flight") || strategy.hasAction("prefer_cheaper")) {
            flightPreference = "cheapest";
            factor = factor.multiply(BigDecimal.valueOf(0.95));
        }
        if (strategy.hasAction("hotel_upgrade") || strategy.hasAction("upgrade_hotel")) {
            hotelCheaper = false;
            if (!travelStyle.toLowerCase().contains("luxury")) {
                travelStyle = "upscale";
            }
        }
        if (strategy.hasAction("remove_expensive_attractions") && !state.attractions().isEmpty()) {
            updates.put(TravelState.ATTRACTIONS, new ArrayList<>(
                    state.attractions().subList(0, Math.min(3, state.attractions().size()))));
        }
        if (strategy.hasAction("add_destination") && state.modification() != null
                && !TravelState.isBlank(state.modification().getDestination())) {
            String extra = state.modification().getDestination();
            String current = TravelState.firstNonBlank(state.destination());
            if (!current.toLowerCase().contains(extra.toLowerCase())) {
                updates.put(TravelState.DESTINATION, current + " and " + extra);
            }
            updates.put(TravelState.NEEDS_RESEARCH, Boolean.TRUE);
            updates.put(TravelState.NEEDS_ITINERARY, Boolean.TRUE);
        }
        if (strategy.hasAction("adjust_itinerary")) {
            updates.put(TravelState.NEEDS_ITINERARY, Boolean.TRUE);
        }

        String notes = "Replan priority=" + strategy.getPriority()
                + " actions=" + strategy.getActions()
                + " cause=" + (TravelState.isBlank(strategy.getReason())
                ? String.join("; ", state.validationErrors()) + " " + String.join("; ", state.semanticNotes())
                : strategy.getReason());

        AgentDecision decision = new AgentDecision("replanner",
                TravelState.firstNonBlank(strategy.getPriority(), "ADJUST").toUpperCase(),
                notes, 0.8);

        updates.put(TravelState.RETRY_COUNT, state.retryCount() + 1);
        updates.put(TravelState.COST_FACTOR, factor);
        updates.put(TravelState.HOTEL_CHEAPER, hotelCheaper);
        updates.put(TravelState.FLIGHT_PREFERENCE, flightPreference);
        updates.put(TravelState.TRAVEL_STYLE, travelStyle);
        updates.put(TravelState.REPLAN_NOTES, notes);
        updates.put(TravelState.REPLAN_STRATEGY, strategy);
        updates.put(TravelState.LAST_DECISION, decision);
        return updates;
    }
}
