package com.example.travel.graph;

import com.example.travel.dto.TravelRequest;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TravelStateDatesTest {
    @Test
    void durationOnlyRequestKeepsDatesFlexible() {
        TravelRequest request = new TravelRequest();
        request.setPrompt("From Bengaluru to Tokyo for 7 days");
        Map<String, Object> input = TravelState.fromRequest(request, "");
        TravelState state = new TravelState(input);
        assertTrue(state.datesFlexible());
        assertTrue(state.roundTrip());
    }
}
