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

    private static final List<String> FALLBACK_ACTIVITIES = List.of(
            "Morning: neighborhood walk and local breakfast. Afternoon: main shopping or park area. Evening: casual dinner.",
            "Morning: landmark / temple or shrine visit. Afternoon: food market tasting. Evening: free time.",
            "Morning: museum or indoor attraction (good rain backup). Afternoon: cafe and shopping street. Evening: local cuisine.",
            "Morning: scenic viewpoint or garden. Afternoon: family-friendly activity. Evening: rest or onsen-style bath if available.",
            "Morning: flexible day — revisit favorites. Afternoon: souvenirs. Evening: farewell dinner."
    );

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

        if (day == 1) {
            // Never keep LLM day-1 content that talks about checkout/departure.
            String modelTitle = fromModel == null ? null : fromModel.getTitle();
            String modelActs = fromModel == null ? null : fromModel.getActivities();
            if (looksLikeDeparture(modelTitle) || looksLikeDeparture(modelActs)) {
                modelTitle = null;
                modelActs = null;
            }
            return new ItineraryDay(1, "Arrival",
                    firstNonBlank(modelActs,
                            "Arrive in " + place + ", airport transfer, and hotel check-in. Light local food nearby."));
        }

        if (day == expected) {
            String modelTitle = fromModel == null ? null : fromModel.getTitle();
            String modelActs = fromModel == null ? null : fromModel.getActivities();
            if (looksLikeArrivalOnly(modelTitle) || looksLikeArrivalOnly(modelActs)) {
                modelActs = null;
            }
            return new ItineraryDay(day, "Departure",
                    firstNonBlank(modelActs,
                            "Hotel checkout, airport transfer, and depart from " + place + "."));
        }

        String title = sanitizeExploreTitle(fromModel == null ? null : fromModel.getTitle(), place, day);
        String activities = firstNonBlank(
                usableExploreActivities(fromModel == null ? null : fromModel.getActivities()),
                activityFor(day, attractions, place));
        return new ItineraryDay(day, title, activities);
    }

    private static String sanitizeExploreTitle(String title, String place, int day) {
        if (title == null || title.isBlank() || looksLikeDeparture(title) || looksLikeArrivalOnly(title)) {
            return "Explore " + place + " — Day " + day;
        }
        return title.trim();
    }

    private static String usableExploreActivities(String activities) {
        if (activities == null || activities.isBlank()) {
            return "";
        }
        if (looksLikeDeparture(activities) || looksLikeArrivalOnly(activities)) {
            return "";
        }
        return activities.trim();
    }

    private static boolean looksLikeDeparture(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.contains("depart") || lower.contains("check-out") || lower.contains("checkout")
                || lower.contains("fly home") || lower.contains("return flight");
    }

    private static boolean looksLikeArrivalOnly(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        boolean arrival = lower.contains("arrival") || lower.contains("arrive") || lower.contains("check-in")
                || lower.contains("check in");
        boolean departure = looksLikeDeparture(lower);
        return arrival && !departure && !lower.contains("explore");
    }

    private static String activityFor(int day, List<TravelAttraction> attractions, String place) {
        List<TravelAttraction> useful = attractions == null ? List.of() : attractions.stream()
                .filter(a -> a != null && a.getName() != null && !a.getName().isBlank())
                .filter(a -> place == null || !a.getName().equalsIgnoreCase(place))
                .filter(a -> !"See destination guide.".equalsIgnoreCase(
                        a.getDescription() == null ? "" : a.getDescription().trim()))
                .toList();

        int exploreIndex = day - 2;
        if (!useful.isEmpty() && useful.size() > 1) {
            TravelAttraction pick = useful.get(Math.floorMod(exploreIndex, useful.size()));
            return formatAttractionDay(pick);
        }
        if (useful.size() == 1 && exploreIndex == 0) {
            return formatAttractionDay(useful.get(0));
        }
        return FALLBACK_ACTIVITIES.get(Math.floorMod(exploreIndex, FALLBACK_ACTIVITIES.size()))
                .replace("neighborhood", place);
    }

    private static String formatAttractionDay(TravelAttraction pick) {
        String area = pick.getArea() == null || pick.getArea().isBlank() ? "" : " (" + pick.getArea() + ")";
        return "Morning: visit " + pick.getName() + area
                + ". Afternoon: local food nearby and a neighborhood walk. Evening: free time.";
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
