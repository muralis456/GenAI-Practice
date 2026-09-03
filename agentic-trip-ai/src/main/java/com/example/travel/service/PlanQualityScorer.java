package com.example.travel.service;

import com.example.travel.graph.TravelState;
import com.example.travel.model.PlanQualityScore;
import com.example.travel.model.SemanticValidationResult;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class PlanQualityScorer {

    public PlanQualityScore score(TravelState state, SemanticValidationResult semantic) {
        PlanQualityScore quality = new PlanQualityScore();
        if (state.budgetSummary() != null) {
            quality.setBudget(state.budgetSummary().isWithinBudget() ? 0.95 : 0.55);
        } else if (!state.needsBudget()) {
            quality.setBudget(1.0);
        } else {
            quality.setBudget(0.7);
        }

        if (!state.needsFlights()) {
            quality.setFlight(1.0);
        } else if (state.flights().isEmpty() || state.flights().stream()
                .allMatch(f -> "unavailable".equalsIgnoreCase(f.getStatus()))) {
            quality.setFlight(0.4);
        } else {
            quality.setFlight(0.85);
        }

        if (!state.needsHotels()) {
            quality.setHotel(1.0);
        } else if (state.hotels().isEmpty()) {
            quality.setHotel(0.5);
        } else {
            quality.setHotel(0.8);
        }

        if (!state.needsItinerary()) {
            quality.setItinerary(1.0);
        } else if (state.itinerary() == null || state.itinerary().isEmpty()) {
            quality.setItinerary(0.45);
        } else {
            quality.setItinerary(semantic == null || !semantic.failed() ? 0.9 : 0.65);
        }

        double preference = 0.85;
        if (semantic != null) {
            preference = Math.max(0.35, semantic.getScore());
        }
        quality.setPreferences(preference);

        if (!state.needsWeather() || state.weather() == null) {
            quality.setWeather(1.0);
        } else if (state.weather().isRainLikely()) {
            quality.setWeather(0.75);
        } else {
            quality.setWeather(0.9);
        }

        quality.recomputeOverall();
        return quality;
    }

    public boolean requestLooksFamily(String request) {
        if (request == null) {
            return false;
        }
        String lower = request.toLowerCase(Locale.ROOT);
        return lower.contains("family") || lower.contains("kid") || lower.contains("children");
    }
}
