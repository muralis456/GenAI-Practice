package com.example.travel.support;

import com.example.travel.model.Itinerary;
import com.example.travel.model.ItineraryDay;
import com.example.travel.model.TravelAttraction;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Ensures itineraries match trip length and arrival/departure expectations
 * even when the LLM returns a short or incomplete day list.
 */
public final class ItinerarySupport {

    private ItinerarySupport() {
    }

    public static int expectedDays(long nights) {
        return (int) Math.max(2, nights + 1);
    }

    public static Itinerary normalize(Itinerary source,
                                      long nights,
                                      String destination,
                                      List<TravelAttraction> attractions) {
        int expected = expectedDays(nights);
        List<ItineraryDay> incoming = source == null || source.getDays() == null
                ? List.of()
                : source.getDays();

        List<ItineraryDay> days = new ArrayList<>();
        for (int i = 1; i <= expected; i++) {
            ItineraryDay fromModel = i <= incoming.size() ? incoming.get(i - 1) : null;
            days.add(buildDay(i, expected, destination, attractions, fromModel));
        }

        String summary = source == null || source.getSummary() == null || source.getSummary().isBlank()
                ? expected + "-day plan for " + (destination == null || destination.isBlank() ? "the trip" : destination)
                : source.getSummary();
        return new Itinerary(summary, days);
    }

    public static Itinerary skeleton(long nights, String destination, List<TravelAttraction> attractions) {
        return normalize(new Itinerary("", List.of()), nights, destination, attractions);
    }

    private static ItineraryDay buildDay(int day,
                                         int expected,
                                         String destination,
                                         List<TravelAttraction> attractions,
                                         ItineraryDay fromModel) {
        String place = destination == null || destination.isBlank() ? "destination" : destination;
        String title;
        String activities;

        if (day == 1) {
            title = ensureKeyword(fromModel == null ? null : fromModel.getTitle(), "Arrival", "arrival", "arrive");
            activities = firstNonBlank(fromModel == null ? null : fromModel.getActivities(),
                    "Arrive in " + place + ", transfer, and hotel check-in. Light local food nearby.");
        } else if (day == expected) {
            title = ensureKeyword(fromModel == null ? null : fromModel.getTitle(), "Departure",
                    "depart", "departure", "return", "checkout", "check-out");
            activities = firstNonBlank(fromModel == null ? null : fromModel.getActivities(),
                    "Hotel checkout, airport transfer, and depart from " + place + ".");
        } else {
            title = firstNonBlank(fromModel == null ? null : fromModel.getTitle(), "Explore " + place);
            activities = firstNonBlank(fromModel == null ? null : fromModel.getActivities(),
                    activityFor(day, attractions, place));
        }

        return new ItineraryDay(day, title, activities);
    }

    private static String activityFor(int day, List<TravelAttraction> attractions, String place) {
        if (attractions != null && !attractions.isEmpty()) {
            TravelAttraction pick = attractions.get(Math.floorMod(day - 2, attractions.size()));
            if (pick.getName() != null && !pick.getName().isBlank()) {
                return "Morning: visit " + pick.getName()
                        + (pick.getArea() == null || pick.getArea().isBlank() ? "" : " (" + pick.getArea() + ")")
                        + ". Afternoon: local food and neighborhood walk. Evening: free time.";
            }
        }
        return "Morning and afternoon sightseeing in " + place + ". Evening: local dinner.";
    }

    private static String ensureKeyword(String title, String fallback, String... keywords) {
        if (title != null && !title.isBlank()) {
            String lower = title.toLowerCase(Locale.ROOT);
            for (String keyword : keywords) {
                if (lower.contains(keyword)) {
                    return title.trim();
                }
            }
            return fallback + " — " + title.trim();
        }
        return fallback;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }
}
