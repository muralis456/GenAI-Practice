package com.example.travel.agent;

import com.example.travel.graph.NodeFailureRouting;
import com.example.travel.graph.NodeFailureSupport;
import com.example.travel.graph.GraphExecutionLogger;
import com.example.travel.graph.TravelState;
import com.example.travel.model.AgentDecision;
import com.example.travel.model.ReplanAction;
import com.example.travel.model.ReplanStrategy;
import com.example.travel.service.ReplanActionValidator;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Applies validated {@link ReplanAction} commands and selective specialist routing.
 */
@Component
public class ReplanStrategyExecutor {

    private final ReplanActionValidator replanActionValidator;

    public ReplanStrategyExecutor(ReplanActionValidator replanActionValidator) {
        this.replanActionValidator = replanActionValidator;
    }

    public Map<String, Object> apply(TravelState state, ReplanStrategy strategy) {
        List<ReplanAction> actions = replanActionValidator.resolve(strategy, state);
        strategy.setResolvedActions(actions);

        Map<String, Object> updates = new LinkedHashMap<>();
        BigDecimal factor = state.costFactor();
        boolean hotelCheaper = state.hotelCheaper();
        String flightPreference = state.flightPreference();
        String travelStyle = state.travelStyle();

        for (ReplanAction action : actions) {
            switch (action) {
                case REDUCE_HOTEL_BUDGET -> {
                    hotelCheaper = true;
                    factor = factor.multiply(BigDecimal.valueOf(0.90));
                }
                case CHEAPER_FLIGHT -> {
                    flightPreference = "cheapest";
                    factor = factor.multiply(BigDecimal.valueOf(0.95));
                }
                case HOTEL_UPGRADE -> {
                    hotelCheaper = false;
                    if (!travelStyle.toLowerCase().contains("luxury")) {
                        travelStyle = "upscale";
                    }
                }
                case REMOVE_EXPENSIVE_ATTRACTIONS -> {
                    if (!state.attractions().isEmpty()) {
                        updates.put(TravelState.ATTRACTIONS, new ArrayList<>(
                                state.attractions().subList(0, Math.min(3, state.attractions().size()))));
                    }
                }
                case ADD_DESTINATION -> {
                    if (state.modification() != null
                            && !TravelState.isBlank(state.modification().getDestination())) {
                        String extra = state.modification().getDestination();
                        String current = TravelState.firstNonBlank(state.destination());
                        if (!current.toLowerCase().contains(extra.toLowerCase())) {
                            updates.put(TravelState.DESTINATION, current + " and " + extra);
                        }
                        updates.put(TravelState.NEEDS_RESEARCH, Boolean.TRUE);
                        updates.put(TravelState.NEEDS_ITINERARY, Boolean.TRUE);
                        updates.put(TravelState.RUN_RESEARCH, Boolean.TRUE);
                        updates.put(TravelState.RUN_ITINERARY, Boolean.TRUE);
                    }
                }
                case ADJUST_ITINERARY -> {
                    updates.put(TravelState.NEEDS_ITINERARY, Boolean.TRUE);
                    updates.put(TravelState.RUN_ITINERARY, Boolean.TRUE);
                }
            }
        }

        applySelectiveRouting(updates, actions, state);
        NodeFailureRouting.applySelectiveNeeds(updates, state);

        String notes = "Replan priority=" + strategy.getPriority()
                + " actions=" + actions
                + " cause=" + (TravelState.isBlank(strategy.getReason())
                ? String.join("; ", state.validationErrors()) + " " + String.join("; ", state.semanticNotes())
                : strategy.getReason());

        AgentDecision decision = new AgentDecision("replanner",
                TravelState.firstNonBlank(strategy.getPriority(), "ADJUST").toUpperCase(),
                notes, strategy.getExpectedImpact() > 0 ? strategy.getExpectedImpact() : 0.8);

        updates.put(TravelState.RETRY_COUNT, state.retryCount() + 1);
        updates.put(TravelState.COST_FACTOR, factor);
        updates.put(TravelState.HOTEL_CHEAPER, hotelCheaper);
        updates.put(TravelState.FLIGHT_PREFERENCE, flightPreference);
        updates.put(TravelState.TRAVEL_STYLE, travelStyle);
        updates.put(TravelState.REPLAN_NOTES, notes);
        updates.put(TravelState.REPLAN_STRATEGY, strategy);
        updates.put(TravelState.LAST_DECISION, decision);
        updates.putAll(NodeFailureSupport.clear());

        Map<String, Object> selectiveNeeds = Map.of(
                "runFlights", updates.getOrDefault(TravelState.RUN_FLIGHTS, state.runFlights()),
                "runHotels", updates.getOrDefault(TravelState.RUN_HOTELS, state.runHotels()),
                "runResearch", updates.getOrDefault(TravelState.RUN_RESEARCH, state.runResearch()),
                "runWeather", updates.getOrDefault(TravelState.RUN_WEATHER, state.runWeather()),
                "runBudget", updates.getOrDefault(TravelState.RUN_BUDGET, state.runBudget()),
                "runItinerary", updates.getOrDefault(TravelState.RUN_ITINERARY, state.runItinerary()),
                "needsFlights", state.needsFlights(),
                "needsHotels", state.needsHotels());
        GraphExecutionLogger.replan(state, actions, selectiveNeeds);
        return updates;
    }

    private void applySelectiveRouting(Map<String, Object> updates, List<ReplanAction> actions, TravelState state) {
        if (actions.isEmpty()) {
            return;
        }
        boolean runFlights = false;
        boolean runHotels = false;
        boolean runResearch = false;
        boolean runWeather = false;
        boolean runBudget = false;
        boolean runItinerary = false;

        for (ReplanAction action : actions) {
            switch (action) {
                case CHEAPER_FLIGHT -> runFlights = true;
                case REDUCE_HOTEL_BUDGET, HOTEL_UPGRADE -> runHotels = true;
                case REMOVE_EXPENSIVE_ATTRACTIONS, ADD_DESTINATION -> runResearch = true;
                case ADJUST_ITINERARY -> runItinerary = true;
            }
        }
        if (runFlights || runHotels || runResearch) {
            runBudget = state.needsBudget();
        }
        if (runItinerary || actions.contains(ReplanAction.ADD_DESTINATION)) {
            runItinerary = true;
        }

        updates.put(TravelState.RUN_FLIGHTS, runFlights);
        updates.put(TravelState.RUN_HOTELS, runHotels);
        updates.put(TravelState.RUN_RESEARCH, runResearch);
        updates.put(TravelState.RUN_WEATHER, runWeather);
        updates.put(TravelState.RUN_BUDGET, runBudget);
        updates.put(TravelState.RUN_ITINERARY, runItinerary);
    }
}
