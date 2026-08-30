package com.example.travel.support;

import com.example.travel.model.IntentPlan;

/**
 * Deterministic intent detection so routing does not depend on an LLM for obvious cases.
 */
public final class IntentClassifier {

    private IntentClassifier() {
    }

    public static IntentPlan classify(String request) {
        String text = request == null ? "" : request.toLowerCase();
        boolean trip = containsAny(text, "plan a", "plan my", "trip", "itinerary", "vacation", "holiday",
                "family", "lakh", "days under", "nights");
        boolean flights = containsAny(text, "flight", "flights", "fly from", "airfare");
        boolean hotels = containsAny(text, "hotel", "hotels", "stay in", "accommodation");
        boolean research = containsAny(text, "places to visit", "sightseeing", "attractions",
                "things to do", "what to see", "recommendations", "best places");
        boolean weather = containsAny(text, "weather", "forecast", "rain");

        if (!trip && weather && !flights && !hotels && !research) {
            return IntentPlan.weatherOnly();
        }
        if (!trip && flights && !hotels && !research) {
            return IntentPlan.flightsOnly();
        }
        if (!trip && hotels && !flights) {
            return IntentPlan.hotelsOnly();
        }
        if (!trip && research && !flights && !hotels) {
            return IntentPlan.researchOnly();
        }
        return IntentPlan.fullTrip();
    }

    private static boolean containsAny(String text, String... needles) {
        for (String needle : needles) {
            if (text.contains(needle)) {
                return true;
            }
        }
        return false;
    }
}
