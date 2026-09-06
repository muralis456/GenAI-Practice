package com.example.travel.service;

import com.example.travel.graph.TravelState;
import com.example.travel.model.Itinerary;
import com.example.travel.model.ItineraryActivity;
import com.example.travel.model.ItineraryDay;
import com.example.travel.model.TripRequirements;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Scores itinerary activities against parsed trip requirements (not raw string matching).
 */
@Component
public class RequirementEvaluator {

    public double familyScore(TravelState state, TripRequirements requirements) {
        if (requirements == null || !requirements.isFamilyFriendly()) {
            return 1.0;
        }
        ActivityCounts counts = countActivities(state);
        if (counts.total == 0) {
            return 0.35;
        }
        double ratio = counts.familyFriendly / (double) counts.total;
        return clamp(0.35 + ratio * 0.65);
    }

    public double foodScore(TravelState state, TripRequirements requirements) {
        if (requirements == null || !requirements.isFoodExperiences()) {
            return 1.0;
        }
        ActivityCounts counts = countActivities(state);
        if (counts.total == 0) {
            return 0.35;
        }
        double ratio = counts.foodExperience / (double) counts.total;
        return clamp(0.35 + ratio * 0.65);
    }

    public double localScore(TravelState state, TripRequirements requirements) {
        if (requirements == null || !requirements.isLocalExperiences()) {
            return 1.0;
        }
        ActivityCounts counts = countActivities(state);
        if (counts.total == 0) {
            return 0.35;
        }
        double ratio = counts.localExperience / (double) counts.total;
        return clamp(0.35 + ratio * 0.65);
    }

    public List<String> evaluateIssues(TravelState state, TripRequirements requirements) {
        List<String> issues = new ArrayList<>();
        if (requirements == null) {
            return issues;
        }
        if (requirements.isFamilyFriendly() && familyScore(state, requirements) < 0.65) {
            issues.add("Itinerary lacks enough family-friendly activities.");
        }
        if (requirements.isFoodExperiences() && foodScore(state, requirements) < 0.65) {
            issues.add("Itinerary lacks enough food experiences.");
        }
        if (requirements.isLocalExperiences() && localScore(state, requirements) < 0.65) {
            issues.add("Itinerary lacks enough local experiences.");
        }
        return issues;
    }

    private ActivityCounts countActivities(TravelState state) {
        ActivityCounts counts = new ActivityCounts();
        Itinerary itinerary = state.itinerary();
        if (itinerary == null || itinerary.getDays() == null) {
            return counts;
        }
        for (ItineraryDay day : itinerary.getDays()) {
            if (day == null || day.getActivities() == null) {
                continue;
            }
            for (ItineraryActivity activity : day.getActivities()) {
                if (activity == null || activity.getName() == null || activity.getName().isBlank()) {
                    continue;
                }
                counts.total++;
                if (activity.isFamilyFriendly()) {
                    counts.familyFriendly++;
                }
                if (activity.isFoodExperience()) {
                    counts.foodExperience++;
                }
                if (activity.isLocalExperience()) {
                    counts.localExperience++;
                }
            }
        }
        return counts;
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static final class ActivityCounts {
        int total;
        int familyFriendly;
        int foodExperience;
        int localExperience;
    }
}
