package com.example.travel.support;

import com.example.travel.model.Itinerary;
import com.example.travel.model.ItineraryDay;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
                List.of());

        assertEqualsDays(normalized, 7);
        assertTrue(normalized.getDays().get(0).getTitle().toLowerCase().contains("arrival"));
        assertTrue(normalized.getDays().get(6).getTitle().toLowerCase().contains("depart"));
    }

    @Test
    void rejectsDepartureContentOnDayOne() {
        Itinerary bad = new Itinerary("bad", List.of(
                new ItineraryDay(1, "Departure and Check-out",
                        "morning: Check-out from hotel, afternoon: Depart from Narita Airport")));

        Itinerary normalized = ItinerarySupport.normalize(bad, 6, "Tokyo", List.of());

        assertFalse(normalized.getDays().get(0).getActivities().toLowerCase().contains("check-out"));
        assertTrue(normalized.getDays().get(0).getActivities().toLowerCase().contains("arrive"));
        assertTrue(normalized.getDays().get(0).getTitle().equalsIgnoreCase("Arrival"));
    }

    private static void assertEqualsDays(Itinerary itinerary, int expected) {
        org.junit.jupiter.api.Assertions.assertEquals(expected, itinerary.getDays().size());
    }
}
