package com.example.travel.agent;

import com.example.travel.config.TravelModelsProperties.AgentRole;
import com.example.travel.graph.TravelState;
import com.example.travel.model.SemanticValidationResult;
import com.example.travel.model.ValidationStatus;
import com.example.travel.service.RoutedLlm;
import com.example.travel.support.JsonSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * LLM judge for preference fit with structured JSON output.
 */
@Service
public class SemanticValidatorService {

    private static final Logger log = LoggerFactory.getLogger(SemanticValidatorService.class);

    private final RoutedLlm routedLlm;
    private final JsonSupport jsonSupport;

    public SemanticValidatorService(RoutedLlm routedLlm, JsonSupport jsonSupport) {
        this.routedLlm = routedLlm;
        this.jsonSupport = jsonSupport;
    }

    public SemanticValidationResult review(TravelState state) {
        SemanticValidationResult result = new SemanticValidationResult();
        result.setStatus(ValidationStatus.PASS);
        result.setScore(1.0);
        if (!state.needsItinerary() || state.itinerary() == null || state.itinerary().isEmpty()) {
            return result;
        }

        List<String> heuristicIssues = heuristicIssues(state);
        result.getIssues().addAll(heuristicIssues);

        try {
            String content = routedLlm.complete(AgentRole.EXTRACT,
                    "You are a semantic travel validator. Return JSON only: "
                            + "{\"status\":\"PASS|FAIL|WARN\",\"score\":0.0,"
                            + "\"issues\":[\"\"],\"recommendedActions\":[\"adjust_itinerary\"]}. "
                            + "Score is 0-1 plan quality for preferences. "
                            + "recommendedActions must be from: reduce_hotel_budget, cheaper_flight, "
                            + "remove_expensive_attractions, hotel_upgrade, adjust_itinerary, add_destination.",
                    "Request: " + state.userRequest()
                            + "\nStyle: " + state.travelStyle()
                            + "\nBudget within ceiling: " + (state.budgetSummary() == null || state.budgetSummary().isWithinBudget())
                            + "\nItinerary:\n" + state.itinerary().toDisplay());
            SemanticValidationResult parsed = jsonSupport.read(content, SemanticValidationResult.class)
                    .orElseGet(SemanticValidationResult::new);
            if (parsed.getStatus() != null) {
                result.setStatus(parsed.getStatus());
            }
            if (parsed.getScore() > 0) {
                result.setScore(parsed.getScore());
            }
            for (String issue : parsed.getIssues()) {
                if (!TravelState.isBlank(issue) && !result.getIssues().contains(issue)) {
                    result.getIssues().add(issue);
                }
            }
            result.setRecommendedActions(parsed.getRecommendedActions());
        } catch (Exception exception) {
            log.warn("Semantic validator LLM failed; using heuristics only", exception);
            if (!heuristicIssues.isEmpty()) {
                result.setStatus(ValidationStatus.WARN);
                result.setScore(0.72);
            }
        }

        if (result.failed() && result.getStatus() != ValidationStatus.FAIL) {
            result.setStatus(ValidationStatus.FAIL);
        }
        return result;
    }

    public List<String> issueNotes(SemanticValidationResult result) {
        return result == null ? List.of() : new ArrayList<>(result.getIssues());
    }

    private List<String> heuristicIssues(TravelState state) {
        List<String> notes = new ArrayList<>();
        String request = state.userRequest().toLowerCase(Locale.ROOT);
        boolean family = request.contains("family") || request.contains("children") || request.contains("kids");
        if (family) {
            String itineraryText = state.itinerary().toDisplay().toLowerCase(Locale.ROOT);
            if (!containsAny(itineraryText, "park", "family", "kid", "museum", "zoo", "aquarium", "garden")) {
                notes.add("Itinerary may not clearly include family-friendly activities.");
            }
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
