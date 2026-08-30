package com.example.travel.agent;

import com.example.travel.config.TravelModelsProperties.AgentRole;
import com.example.travel.graph.TravelState;
import com.example.travel.service.RoutedLlm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * LLM judge for preference fit. Does not replace deterministic Validator checks.
 */
@Service
public class SemanticValidatorService {

    private static final Logger log = LoggerFactory.getLogger(SemanticValidatorService.class);

    private final RoutedLlm routedLlm;

    public SemanticValidatorService(RoutedLlm routedLlm) {
        this.routedLlm = routedLlm;
    }

    public List<String> review(TravelState state) {
        if (!state.needsItinerary() || state.itinerary() == null || state.itinerary().isEmpty()) {
            return List.of();
        }
        String request = state.userRequest().toLowerCase(Locale.ROOT);
        boolean family = request.contains("family") || request.contains("children") || request.contains("kids");
        List<String> notes = new ArrayList<>();
        if (family) {
            String itineraryText = state.itinerary().toDisplay().toLowerCase(Locale.ROOT);
            if (!containsAny(itineraryText, "park", "family", "kid", "museum", "zoo", "aquarium", "garden")) {
                notes.add("Itinerary may not clearly include family-friendly activities.");
            }
        }
        try {
            String verdict = routedLlm.complete(AgentRole.EXTRACT,
                    "You are a semantic travel validator. Answer with PASS or a short FAIL reason. "
                            + "Do not invent missing flights or prices. Judge only whether the itinerary fits the user request.",
                    "Request: " + state.userRequest()
                            + "\nStyle: " + state.travelStyle()
                            + "\nItinerary:\n" + state.itinerary().toDisplay());
            if (verdict != null && verdict.toUpperCase(Locale.ROOT).contains("FAIL")) {
                String reason = verdict.replaceAll("(?i)FAIL[:\\-]*", "").trim();
                if (!TravelState.isBlank(reason)) {
                    notes.add(reason.length() > 240 ? reason.substring(0, 240) : reason);
                }
            }
        } catch (Exception exception) {
            log.warn("Semantic validator LLM failed; keeping heuristic notes only", exception);
        }
        return notes;
    }

    private boolean containsAny(String text, String... needles) {
        for (String needle : needles) {
            if (text.contains(needle)) {
                return true;
            }
        }
        return false;
    }
}
