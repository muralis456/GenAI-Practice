package com.example.travel.support;

import com.example.travel.model.Itinerary;
import com.example.travel.model.ItineraryDay;
import com.example.travel.model.TravelAttraction;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItinerarySupportTest {

    @Test
    void padsShortLlmItineraryToTripLengthWithArrivalAndDeparture() {
        Itinerary shortPlan = new Itinerary("Tokyo trip", List.of(
                new ItineraryDay(1, "City walk", "Visit Shibuya")));

        Itinerary normalized = ItinerarySupport.normalize(
                shortPlan,
                6,
                "Tokyo",
                List.of(new TravelAttraction("Senso-ji", "Temple", "Asakusa")));

        assertEquals(7, normalized.getDays().size());
        assertTrue(normalized.getDays().get(0).getTitle().toLowerCase().contains("arrival"));
        assertTrue(normalized.getDays().get(6).getTitle().toLowerCase().contains("depart"));
        assertEquals(1, normalized.getDays().get(0).getDay());
        assertEquals(7, normalized.getDays().get(6).getDay());
    }
}
