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
    private static final Pattern ROUTE = Pattern.compile(
            "(?i)\\bfrom\\s+(.+?)\\s+to\\s+(.+?)(?=\\s+for\\s+|\\s+on\\s+|\\s+today\\b|[.!?]|$)");
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
            case "uae", "united arab emirates", "dubaig", "dubia" -> "Dubai";
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

    public static String extractOriginHint(String request) {
        if (request == null || request.isBlank()) return "";
        Matcher route = ROUTE.matcher(request);
        if (route.find()) return normalizePlace(route.group(1));
        return findKnownPlace(request, false);
    }

    public static String extractDestinationHint(String request) {
        if (request == null || request.isBlank()) return "";
        Matcher route = ROUTE.matcher(request);
        if (route.find()) {
            return normalizePlace(cleanDestination(route.group(2)));
        }
        return findKnownPlace(request, true);
    }

    /**
     * Route extraction must stop before trip constraints and instructions.
     * Example: "from Bangalore to dubai within the budget 2L. give me..."
     * must produce "Dubai", not "dubai within the budget 2L".
     */
    private static String cleanDestination(String value) {
        if (value == null || value.isBlank()) return "";
        String cleaned = value.trim();
        cleaned = cleaned.replaceFirst("(?i)\\s+(?:within|under|below)\\s+(?:the\\s+)?budget(?:\\s+of)?\\b.*$", "");
        cleaned = cleaned.replaceFirst("(?i)\\s+(?:within|under|below)\\s+(?:the\\s+)?\\d+\\s*(?:lakh|lac|l)\\b.*$", "");
        cleaned = cleaned.replaceFirst("(?i)\\s+(?:with|on)\\s+(?:a\\s+)?budget\\b.*$", "");
        cleaned = cleaned.replaceFirst("(?i)\\s+(?:for|over)\\s+\\d{1,2}\\s*-?\\s*(?:day|night)s?\\b.*$", "");
        cleaned = cleaned.replaceFirst("(?i)\\s+(?:give|show|find|provide)\\s+me\\b.*$", "");
        cleaned = cleaned.replaceFirst("[,.!?;:]+$", "").trim();
        return cleaned;
    }

    private static String findKnownPlace(String request, boolean destination) {
        String lower = request.toLowerCase(Locale.ROOT);
        String[] places = {
                "tokyo", "japan", "osaka", "kyoto", "bangkok", "thailand", "dubai", "dubaig", "dubia", "uae",
                "paris", "london", "singapore", "bali", "beijing", "shanghai", "seoul",
                "mumbai", "delhi", "bengaluru", "bangalore", "goa", "new york", "rome"
        };
        String found = "";
        for (String place : places) {
            if (lower.contains(place)) {
                if (!destination) return normalizePlace(place);
                found = normalizePlace(place);
            }
        }
        return found;
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
