package com.example.travel.eval;

import com.example.travel.graph.TravelState;
import com.example.travel.model.IntentPlan;
import com.example.travel.agent.ValidatorAgentService;
import com.example.travel.support.IntentClassifier;
import com.example.travel.support.ToolFailureClassifier;
import com.example.travel.tool.ToolErrorCode;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lightweight evaluation harness: routing, validation, and tool-failure policy
 * without calling Ollama or live APIs.
 */
class AgentEvaluationTest {

    @Test
    void intentRoutesFlightOnlyQueryAwayFromHotelsAndItinerary() {
        IntentPlan plan = IntentClassifier.classify("Find flights from BLR to Paris");
        assertEquals(IntentPlan.FLIGHT_SEARCH, plan.getRequestType());
        assertTrue(plan.isNeedsFlights());
        assertFalse(plan.isNeedsHotels());
        assertFalse(plan.isNeedsItinerary());
        assertFalse(plan.isNeedsResearch());
    }

    @Test
    void intentRoutesSightseeingAwayFromAviationStack() {
        IntentPlan plan = IntentClassifier.classify("What are the best places to visit in Paris?");
        assertEquals(IntentPlan.RESEARCH, plan.getRequestType());
        assertTrue(plan.isNeedsResearch());
        assertTrue(plan.isNeedsWeather());
        assertFalse(plan.isNeedsFlights());
        assertFalse(plan.isNeedsHotels());
    }

    @Test
    void intentKeepsFullTripPlanning() {
        IntentPlan plan = IntentClassifier.classify("Plan a 10-day Japan trip under 2 lakh for a family");
        assertEquals(IntentPlan.TRIP_PLANNING, plan.getRequestType());
        assertTrue(plan.isNeedsFlights());
        assertTrue(plan.isNeedsHotels());
        assertTrue(plan.isNeedsItinerary());
    }

    @Test
    void deterministicValidatorSkipsFlightRouteWhenFlightsNotNeeded() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put(TravelState.DESTINATION, "Paris");
        data.put(TravelState.DEPARTURE_DATE, LocalDate.now());
        data.put(TravelState.RETURN_DATE, LocalDate.now().plusDays(4));
        data.put(TravelState.NEEDS_FLIGHTS, Boolean.FALSE);
        data.put(TravelState.NEEDS_ITINERARY, Boolean.FALSE);
        data.put(TravelState.ORIGIN_IATA, "BLR");
        data.put(TravelState.DESTINATION_IATA, "CDG");
        TravelState state = new TravelState(data);
        List<String> errors = new ValidatorAgentService().validate(state);
        assertTrue(errors.stream().noneMatch(e -> e.contains("flight")), errors::toString);
        assertTrue(errors.stream().noneMatch(e -> e.contains("Itinerary")), errors::toString);
    }

    @Test
    void toolFailureClassifierDoesNotRetryForbiddenOrUnprocessable() {
        assertEquals(ToolErrorCode.FORBIDDEN, ToolFailureClassifier.fromHttp(403, "function_access_restricted"));
        assertFalse(ToolFailureClassifier.fromHttp(403, "").isRetryable());
        assertEquals(ToolErrorCode.INVALID_INPUT, ToolFailureClassifier.fromHttp(422, "query null"));
        assertFalse(ToolFailureClassifier.fromHttp(422, "").isRetryable());
        assertTrue(ToolFailureClassifier.fromHttp(429, "rate limit").isRetryable());
        assertTrue(ToolFailureClassifier.fromHttp(503, "").isRetryable());
    }
}
