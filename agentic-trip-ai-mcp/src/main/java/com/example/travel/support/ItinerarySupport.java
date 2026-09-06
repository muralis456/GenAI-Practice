package com.example.travel.support;

import com.example.travel.model.Itinerary;
import com.example.travel.model.ItineraryActivity;
import com.example.travel.model.ItineraryDay;
import com.example.travel.model.TravelAttraction;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Ensures itineraries match trip length and arrival/departure expectations
 * with structured {@link ItineraryActivity} entries.
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

        if (day == 1) {
            if (fromModel != null && (looksLikeDeparture(fromModel.getTitle())
                    || dayText(fromModel).contains("depart"))) {
                fromModel = null;
            }
            List<ItineraryActivity> activities = fromModel == null || fromModel.getActivities().isEmpty()
                    ? arrivalActivities(place)
                    : sanitizeActivities(fromModel.getActivities());
            return new ItineraryDay(1, "Arrival", activities);
        }

        if (day == expected) {
            List<ItineraryActivity> activities = fromModel == null || fromModel.getActivities().isEmpty()
                    ? departureActivities(place)
                    : sanitizeActivities(fromModel.getActivities());
            return new ItineraryDay(day, "Departure", activities);
        }

        String title = sanitizeExploreTitle(fromModel == null ? null : fromModel.getTitle(), place, day);
        List<ItineraryActivity> activities = fromModel != null && !fromModel.getActivities().isEmpty()
                ? sanitizeActivities(fromModel.getActivities())
                : exploreActivities(day, attractions, place);
        return new ItineraryDay(day, title, activities);
    }

    private static List<ItineraryActivity> arrivalActivities(String place) {
        return List.of(
                activity("Arrive at " + place, "transport", "mixed", true, false, false),
                activity("Hotel check-in", "lodging", "indoor", true, false, false),
                activity("Light local dinner nearby", "food", "outdoor", true, true, true));
    }

    private static List<ItineraryActivity> departureActivities(String place) {
        return List.of(
                activity("Hotel checkout", "lodging", "indoor", true, false, false),
                activity("Airport transfer", "transport", "mixed", true, false, false),
                activity("Depart from " + place, "transport", "mixed", true, false, false));
    }

    private static List<ItineraryActivity> exploreActivities(int day,
                                                              List<TravelAttraction> attractions,
                                                              String place) {
        List<TravelAttraction> useful = attractions == null ? List.of() : attractions.stream()
                .filter(a -> a != null && a.getName() != null && !a.getName().isBlank())
                .filter(a -> place == null || !a.getName().equalsIgnoreCase(place))
                .toList();

        int exploreIndex = day - 2;
        if (!useful.isEmpty()) {
            TravelAttraction pick = useful.get(Math.floorMod(exploreIndex, useful.size()));
            String area = pick.getArea() == null || pick.getArea().isBlank() ? "" : " (" + pick.getArea() + ")";
            return List.of(
                    activity("Visit " + pick.getName() + area, "sightseeing", "outdoor", true, false, true),
                    activity("Local food nearby", "food", "outdoor", true, true, true),
                    activity("Neighborhood walk", "leisure", "outdoor", true, false, true));
        }

        return switch (Math.floorMod(exploreIndex, 4)) {
            case 0 -> List.of(
                    activity("Neighborhood walk in " + place, "leisure", "outdoor", true, false, true),
                    activity("Local breakfast spot", "food", "indoor", true, true, true),
                    activity("Evening casual dinner", "food", "outdoor", true, true, true));
            case 1 -> List.of(
                    activity("Landmark or shrine visit", "culture", "outdoor", true, false, true),
                    activity("Food market tasting", "food", "outdoor", true, true, true),
                    activity("Free evening", "leisure", "mixed", true, false, false));
            case 2 -> List.of(
                    activity("Museum or indoor attraction", "culture", "indoor", true, true, false),
                    activity("Cafe and shopping street", "shopping", "outdoor", true, false, true),
                    activity("Local cuisine dinner", "food", "indoor", true, true, true));
            default -> List.of(
                    activity("Scenic garden or park", "nature", "outdoor", true, true, false),
                    activity("Family-friendly activity", "family", "mixed", true, true, false),
                    activity("Farewell dinner", "food", "indoor", true, true, true));
        };
    }

    private static List<ItineraryActivity> sanitizeActivities(List<ItineraryActivity> incoming) {
        List<ItineraryActivity> cleaned = new ArrayList<>();
        for (ItineraryActivity item : incoming) {
            if (item == null || item.getName() == null || item.getName().isBlank()) {
                continue;
            }
            String lower = item.getName().toLowerCase(Locale.ROOT);
            if (lower.contains("(indoor activity)") || lower.contains("(outdoor activity)")) {
                item.setName(item.getName().replaceAll("(?i)\\((indoor|outdoor) activity\\)", "").trim());
            }
            if (item.getIndoorOutdoor() == null || item.getIndoorOutdoor().isBlank()) {
                item.setIndoorOutdoor(lower.contains("museum") || lower.contains("indoor") ? "indoor" : "outdoor");
            }
            cleaned.add(item);
        }
        return cleaned.isEmpty() ? List.of() : cleaned;
    }

    private static ItineraryActivity activity(String name,
                                              String type,
                                              String indoorOutdoor,
                                              boolean familyFriendly,
                                              boolean foodExperience,
                                              boolean localExperience) {
        return new ItineraryActivity(name, type, indoorOutdoor, familyFriendly, foodExperience, localExperience);
    }

    private static String dayText(ItineraryDay day) {
        return (day.getTitle() == null ? "" : day.getTitle()) + " " + day.activitiesText();
    }

    private static String sanitizeExploreTitle(String title, String place, int day) {
        if (title == null || title.isBlank() || looksLikeDeparture(title) || looksLikeArrivalOnly(title)) {
            return "Explore " + place + " — Day " + day;
        }
        return title.trim();
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
}
