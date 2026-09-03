package com.example.travel.graph;

import com.example.travel.agent.ReplanStrategyExecutor;
import com.example.travel.agent.SupervisorAgentService;
import com.example.travel.model.PlanQualityScore;
import com.example.travel.model.ReplanStrategy;
import com.example.travel.service.ReplanActionValidator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Graph routing and replan transitions without Spring or live LLM calls.
 */
class GraphTransitionTest {

    private final ReplanStrategyExecutor replanExecutor = new ReplanStrategyExecutor(new ReplanActionValidator());

    @Test
    void plannerRoutesToAirportWhenFlightsNeeded() {
        TravelState state = state(Map.of(
                TravelState.NEEDS_FLIGHTS, Boolean.TRUE,
                TravelState.NEEDS_HOTELS, Boolean.TRUE));
        assertEquals(TravelGraphNodes.AIRPORT, SpecialistRouter.afterPlanner(state));
    }

    @Test
    void plannerRoutesToFanOutWhenFlightsSkippedButHotelsNeeded() {
        TravelState state = state(Map.of(
                TravelState.NEEDS_FLIGHTS, Boolean.FALSE,
                TravelState.NEEDS_HOTELS, Boolean.TRUE,
                TravelState.NEEDS_RESEARCH, Boolean.TRUE,
                TravelState.NEEDS_WEATHER, Boolean.FALSE));
        assertEquals(TravelGraphNodes.FAN_OUT, SpecialistRouter.afterPlanner(state));
        assertEquals(List.of(TravelGraphNodes.HOTEL, TravelGraphNodes.RESEARCH),
                SpecialistRouter.plannedSpecialists(state));
    }

    @Test
    void plannerSkipsSpecialistsWhenNothingRequested() {
        TravelState state = state(Map.of(
                TravelState.NEEDS_FLIGHTS, Boolean.FALSE,
                TravelState.NEEDS_HOTELS, Boolean.FALSE,
                TravelState.NEEDS_RESEARCH, Boolean.FALSE,
                TravelState.NEEDS_WEATHER, Boolean.FALSE,
                TravelState.NEEDS_BUDGET, Boolean.TRUE));
        assertEquals(TravelGraphNodes.SUPERVISOR, SpecialistRouter.afterPlanner(state));
    }

    @Test
    void supervisorRetryRoutesToReplan() {
        TravelState state = state(Map.of(
                TravelState.SUPERVISOR_DECISION, TravelGraphNodes.ROUTE_RETRY));
        assertEquals(TravelGraphNodes.REPLAN, SpecialistRouter.afterSupervisor(state));
    }

    @Test
    void supervisorProceedSkipsBudgetWhenNotNeeded() {
        TravelState state = state(Map.of(
                TravelState.SUPERVISOR_DECISION, TravelGraphNodes.ROUTE_PROCEED,
                TravelState.NEEDS_BUDGET, Boolean.FALSE,
                TravelState.NEEDS_ITINERARY, Boolean.TRUE));
        assertEquals(TravelGraphNodes.ITINERARY, SpecialistRouter.afterSupervisor(state));
    }

    @Test
    void lowPlanQualityTriggersReplan() {
        PlanQualityScore score = new PlanQualityScore();
        score.setOverall(0.55);
        TravelState state = state(Map.of(
                TravelState.PLAN_QUALITY, score,
                TravelState.RETRY_COUNT, 0,
                TravelState.MAX_RETRIES, 2,
                TravelState.VALIDATION_ERRORS, List.of()));
        assertTrue(state.shouldReplan());
    }

    @Test
    void replanCheaperFlightSelectsOnlyFlightAndBudget() {
        TravelState state = state(Map.of(
                TravelState.COST_FACTOR, BigDecimal.ONE,
                TravelState.TRAVEL_STYLE, "balanced",
                TravelState.HOTEL_CHEAPER, Boolean.FALSE,
                TravelState.FLIGHT_PREFERENCE, "balanced",
                TravelState.RETRY_COUNT, 0,
                TravelState.NEEDS_FLIGHTS, Boolean.TRUE,
                TravelState.NEEDS_HOTELS, Boolean.TRUE,
                TravelState.NEEDS_BUDGET, Boolean.TRUE,
                TravelState.NEEDS_ITINERARY, Boolean.TRUE));
        ReplanStrategy strategy = new ReplanStrategy();
        strategy.setActions(List.of("cheaper_flight"));
        Map<String, Object> updates = replanExecutor.apply(state, strategy);
        assertEquals(Boolean.TRUE, updates.get(TravelState.NEEDS_FLIGHTS));
        assertEquals(Boolean.TRUE, updates.get(TravelState.NEEDS_BUDGET));
        assertEquals(Boolean.FALSE, updates.get(TravelState.NEEDS_HOTELS));
        assertEquals(Boolean.FALSE, updates.get(TravelState.NEEDS_ITINERARY));
    }

    @Test
    void supervisorRetriesWhenRequiredFlightsMissing() {
        TravelState state = state(Map.of(
                TravelState.NEEDS_FLIGHTS, Boolean.TRUE,
                TravelState.FLIGHTS, List.of(),
                TravelState.RETRY_COUNT, 0,
                TravelState.MAX_RETRIES, 2));
        assertEquals(TravelGraphNodes.ROUTE_RETRY, new SupervisorAgentService(null, null).decide(state));
    }

    @Test
    void supervisorProceedsAtMaxRetriesEvenWhenFlightsMissing() {
        TravelState state = state(Map.of(
                TravelState.NEEDS_FLIGHTS, Boolean.TRUE,
                TravelState.FLIGHTS, List.of(),
                TravelState.RETRY_COUNT, 2,
                TravelState.MAX_RETRIES, 2));
        assertEquals(TravelGraphNodes.ROUTE_PROCEED, new SupervisorAgentService(null, null).decide(state));
    }

    private static TravelState state(Map<String, Object> values) {
        Map<String, Object> data = new LinkedHashMap<>(values);
        return new TravelState(data);
    }
}
