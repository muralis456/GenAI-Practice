package com.example.travel.graph;

import com.example.travel.dto.TravelRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TravelStateTest {

    @Test
    void unsetBudgetIsExposedAsNullCeiling() {
        TravelState state = new TravelState(Map.of(TravelState.BUDGET, TravelState.UNSET_BUDGET));
        assertEquals(null, state.budget());
        assertNotNull(TravelState.SCHEMA.get(TravelState.BUDGET));
        assertNotNull(TravelState.SCHEMA.get(TravelState.ITINERARY));
        assertNotNull(TravelState.SCHEMA.get(TravelState.WEATHER));
    }

    @Test
    void fromRequestMapsCoreTripSlots() {
        TravelRequest request = new TravelRequest();
        request.setUserId("u1");
        request.setDestination("Beijing");
        request.setDepartureCity("Mumbai");
        request.setDepartureDate("2026-09-15");
        request.setReturnDate("2026-09-20");
        request.setAdults(2);
        request.setChildren(1);
        request.setBudget("₹200000");
        request.setPrompt("Family trip from Mumbai to Beijing");

        Map<String, Object> input = TravelState.fromRequest(request, "previous context");
        TravelState state = new TravelState(input);

        assertEquals("u1", state.userId());
        assertEquals("Beijing", state.destination());
        assertEquals("Mumbai", state.origin());
        assertEquals(LocalDate.parse("2026-09-15"), state.departureDate());
        assertEquals(3, state.travelers());
        assertEquals(new BigDecimal("200000"), state.budget());
        assertEquals(new BigDecimal("200000"), TravelState.parseBudget("2 lakh"));
        assertEquals(new BigDecimal("200000"), TravelState.parseBudget("₹2L"));
        assertTrue(state.historyContext().contains("previous"));
    }
}
