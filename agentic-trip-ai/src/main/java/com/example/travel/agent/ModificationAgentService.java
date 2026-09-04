package com.example.travel.agent;

import com.example.travel.config.TravelModelsProperties.AgentRole;
import com.example.travel.graph.TravelState;
import com.example.travel.model.ModificationRequest;
import com.example.travel.service.RoutedLlm;
import com.example.travel.support.JsonSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ModificationAgentService {

    private static final Logger log = LoggerFactory.getLogger(ModificationAgentService.class);
    private static final Pattern HOTEL_BUDGET_PATTERN = Pattern.compile(
            "(?:hotel\\s+budget|hotels?\\s+(?:under|below|max)|budget\\s+for\\s+hotels?)\\s*[:=]?\\s*([\\d,]+(?:\\.\\d+)?\\s*(?:lakh|lac|l|k)?)",
            Pattern.CASE_INSENSITIVE);

    private final RoutedLlm routedLlm;
    private final JsonSupport jsonSupport;

    public ModificationAgentService(RoutedLlm routedLlm, JsonSupport jsonSupport) {
        this.routedLlm = routedLlm;
        this.jsonSupport = jsonSupport;
    }

    public ModificationRequest interpret(TravelState state, String notes) {
        ModificationRequest heuristic = heuristic(notes);
        try {
            String content = routedLlm.complete(AgentRole.PLANNER,
                    "You are the Modification Agent. Map the user's change request to JSON only: "
                            + "{\"changeType\":\"REDUCE_COST|HOTEL_UPGRADE|ADD_DESTINATION|ITINERARY_CHANGE|GENERAL\","
                            + "\"destination\":\"\",\"days\":null,\"targetRating\":\"\",\"preserveBudget\":true,"
                            + "\"hotelBudget\":null,\"flightPreference\":\"\","
                            + "\"notes\":\"\"}. "
                            + "hotelBudget is a numeric INR ceiling when the user sets a hotel budget. "
                            + "flightPreference is cheapest|direct|morning|evening|balanced when stated. "
                            + "Do not assume cheaper unless the user asked to save money.",
                    "Current destination=" + state.destination()
                            + " style=" + state.travelStyle()
                            + " flightPreference=" + state.flightPreference()
                            + "\nUser modification: " + notes);
            return jsonSupport.read(content, ModificationRequest.class)
                    .map(parsed -> merge(heuristic, parsed, notes))
                    .orElse(heuristic);
        } catch (Exception exception) {
            log.warn("Modification LLM failed; using heuristic", exception);
            return heuristic;
        }
    }

    static ModificationRequest merge(ModificationRequest heuristic, ModificationRequest parsed, String notes) {
        if (TravelState.isBlank(parsed.getNotes())) {
            parsed.setNotes(notes);
        }
        if (parsed.getHotelBudget() == null) {
            parsed.setHotelBudget(heuristic.getHotelBudget());
        }
        if (TravelState.isBlank(parsed.getFlightPreference())) {
            parsed.setFlightPreference(heuristic.getFlightPreference());
        }
        if (ModificationRequest.GENERAL.equalsIgnoreCase(parsed.getChangeType())
                && !ModificationRequest.GENERAL.equalsIgnoreCase(heuristic.getChangeType())) {
            parsed.setChangeType(heuristic.getChangeType());
        }
        return parsed;
    }

    public static ModificationRequest heuristic(String notes) {
        ModificationRequest request = new ModificationRequest();
        request.setNotes(notes == null ? "" : notes);
        String text = request.getNotes().toLowerCase(Locale.ROOT);
        request.setHotelBudget(parseHotelBudget(text));
        request.setFlightPreference(parseFlightPreference(text));
        if (containsAny(text, "upgrade", "better hotel", "5 star", "five star", "nicer hotel", "luxury hotel")) {
            request.setChangeType(ModificationRequest.HOTEL_UPGRADE);
            request.setPreserveBudget(false);
            return request;
        }
        if (containsAny(text, "add ", "also visit", "include ", "extra day", "more days")) {
            request.setChangeType(ModificationRequest.ADD_DESTINATION);
            request.setPreserveBudget(true);
            return request;
        }
        if (containsAny(text, "cheaper", "budget", "reduce cost", "lower cost", "save money", "too expensive",
            "hotel price", "hotel prices", "hotel rate", "hotel rates", "hotel cost", "hotel costs",
            "hotel is expensive", "hotels are expensive", "hotel is high", "hotels are high",
            "flight price", "flight prices", "flight fare", "flight fares", "flight cost", "flight costs",
            "flight is expensive", "flights are expensive", "flight is high", "flights are high")) {
            request.setChangeType(ModificationRequest.REDUCE_COST);
            request.setPreserveBudget(false);
            return request;
        }
        if (containsAny(text, "itinerary", "swap", "move day", "indoor")) {
            request.setChangeType(ModificationRequest.ITINERARY_CHANGE);
            request.setPreserveBudget(true);
            return request;
        }
        request.setChangeType(ModificationRequest.GENERAL);
        request.setPreserveBudget(true);
        return request;
    }

    static BigDecimal parseHotelBudget(String text) {
        Matcher matcher = HOTEL_BUDGET_PATTERN.matcher(text);
        if (matcher.find()) {
            return TravelState.parseBudget(matcher.group(1));
        }
        if (text.contains("hotel budget") || text.contains("for hotels")) {
            return TravelState.parseBudget(text);
        }
        return null;
    }

    static String parseFlightPreference(String text) {
        if (containsAny(text, "cheapest flight", "cheaper flight", "lowest fare")) {
            return "cheapest";
        }
        if (containsAny(text, "direct flight", "non-stop", "nonstop")) {
            return "direct";
        }
        if (containsAny(text, "morning flight", "early flight")) {
            return "morning";
        }
        if (containsAny(text, "evening flight", "late flight", "night flight")) {
            return "evening";
        }
        return "";
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
