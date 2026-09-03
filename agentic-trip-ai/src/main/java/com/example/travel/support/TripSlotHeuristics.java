package com.example.travel.support;

import java.time.LocalDate;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic slot helpers used when the planner LLM misses budget/dates/destination.
 */
public final class TripSlotHeuristics {

    private static final Pattern DAYS = Pattern.compile("(?i)\\b(\\d{1,2})\\s*-?\\s*day");
    private static final Pattern NIGHTS = Pattern.compile("(?i)\\b(\\d{1,2})\\s*-?\\s*night");
    private static final Pattern BUDGET = Pattern.compile(
            "(?i)(?:under|below|within|budget(?:\\s+of)?)\\s*(₹\\s*)?([\\d,.]+\\s*(?:lakh|lac|l)\\b|[\\d,.]+)");
    private static final Pattern BUDGET_INLINE = Pattern.compile("(?i)(₹\\s*[\\d,.]+\\s*(?:lakh|lac|l)?|[\\d,.]+\\s*(?:lakh|lac|l)\\b)");

    private TripSlotHeuristics() {
    }

    public static String normalizePlace(String place) {
        if (place == null || place.isBlank()) {
            return "";
        }
        String value = place.trim();
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "japan", "nippon" -> "Tokyo";
            case "thailand" -> "Bangkok";
            case "uae", "united arab emirates" -> "Dubai";
            case "uk", "united kingdom", "england" -> "London";
            case "usa", "united states", "america" -> "New York";
            case "bangalore" -> "Bengaluru";
            case "bombay" -> "Mumbai";
            case "calcutta" -> "Kolkata";
            case "madras" -> "Chennai";
            case "new delhi" -> "Delhi";
            default -> value;
        };
    }

    public static String extractDestinationHint(String request) {
        if (request == null || request.isBlank()) {
            return "";
        }
        String lower = request.toLowerCase(Locale.ROOT);
        String[] places = {
                "tokyo", "japan", "osaka", "kyoto", "bangkok", "thailand", "dubai", "uae",
                "paris", "london", "singapore", "bali", "beijing", "shanghai", "seoul",
                "mumbai", "delhi", "bengaluru", "bangalore", "goa", "new york", "rome"
        };
        for (String place : places) {
            if (lower.contains(place)) {
                return normalizePlace(place);
            }
        }
        return "";
    }

    public static String extractBudgetLabel(String request) {
        if (request == null || request.isBlank()) {
            return "";
        }
        Matcher under = BUDGET.matcher(request);
        if (under.find()) {
            return (under.group(1) == null ? "" : under.group(1)) + under.group(2).trim();
        }
        Matcher inline = BUDGET_INLINE.matcher(request);
        if (inline.find()) {
            return inline.group(1).trim();
        }
        return "";
    }

    public static LocalDate inferReturnDate(String request, LocalDate departure, LocalDate fallbackReturn) {
        LocalDate start = departure == null ? LocalDate.now() : departure;
        Matcher days = DAYS.matcher(request == null ? "" : request);
        if (days.find()) {
            int count = Integer.parseInt(days.group(1));
            return start.plusDays(Math.max(1, count - 1));
        }
        Matcher nights = NIGHTS.matcher(request == null ? "" : request);
        if (nights.find()) {
            int count = Integer.parseInt(nights.group(1));
            return start.plusDays(Math.max(1, count));
        }
        return fallbackReturn == null ? start.plusDays(5) : fallbackReturn;
    }
}
