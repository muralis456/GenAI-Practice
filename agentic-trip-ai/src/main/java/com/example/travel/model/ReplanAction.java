package com.example.travel.model;

import com.example.travel.graph.TravelState;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Typed replan commands. LLM output is mapped here; executor validates preconditions.
 */
public enum ReplanAction {
    REDUCE_HOTEL_BUDGET("reduce_hotel_budget"),
    CHEAPER_FLIGHT("cheaper_flight", "prefer_cheaper"),
    REMOVE_EXPENSIVE_ATTRACTIONS("remove_expensive_attractions"),
    HOTEL_UPGRADE("hotel_upgrade", "upgrade_hotel"),
    ADJUST_ITINERARY("adjust_itinerary"),
    ADD_DESTINATION("add_destination");

    private final List<String> aliases;

    ReplanAction(String... aliases) {
        this.aliases = Arrays.asList(aliases);
    }

    public static Optional<ReplanAction> fromToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        String normalized = token.trim().toLowerCase(Locale.ROOT);
        for (ReplanAction action : values()) {
            for (String alias : action.aliases) {
                if (normalized.equals(alias) || normalized.contains(alias)) {
                    return Optional.of(action);
                }
            }
        }
        return Optional.empty();
    }

    public boolean isAllowed(TravelState state) {
        return switch (this) {
            case CHEAPER_FLIGHT -> state.needsFlights();
            case REDUCE_HOTEL_BUDGET, HOTEL_UPGRADE -> state.needsHotels();
            case REMOVE_EXPENSIVE_ATTRACTIONS, ADD_DESTINATION -> state.needsResearch() || !state.attractions().isEmpty();
            case ADJUST_ITINERARY -> state.needsItinerary();
        };
    }

    public String wireName() {
        return aliases.get(0);
    }
}
