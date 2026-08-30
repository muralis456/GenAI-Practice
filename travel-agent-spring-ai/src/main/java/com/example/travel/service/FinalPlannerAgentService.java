package com.example.travel.service;

import com.example.travel.graph.TravelState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.example.travel.config.TravelModelsProperties.AgentRole;
import org.springframework.stereotype.Service;

@Service
public class FinalPlannerAgentService {

    private static final Logger log = LoggerFactory.getLogger(FinalPlannerAgentService.class);

    private final RoutedLlm routedLlm;

    public FinalPlannerAgentService(RoutedLlm routedLlm) {
        this.routedLlm = routedLlm;
    }

    public String compose(TravelState state) {
        log.info("Final agent composing plan for destination={}", state.destination());
        String itinerary = state.itinerary() == null ? "" : state.itinerary().toDisplay();
        String budget = state.budgetSummary() == null ? state.budgetLabel() : state.budgetSummary().toDisplay();
        try {
            String plan = routedLlm.complete(AgentRole.FINAL,
                    "You are the Final Travel Agent. Write a clear trip plan for the user using only the supplied state. "
                            + "Never invent flight numbers. If flights are unavailable, say so. Do not use bracketed placeholders.",
                    """
                            User request: %s
                            Origin: %s
                            Destination: %s
                            Dates: %s to %s
                            Travelers: %s
                            Style: %s
                            Flights: %s
                            Hotels: %s
                            Research: %s
                            Attractions: %s
                            Budget: %s
                            Itinerary:
                            %s
                            Validation notes: %s
                            """.formatted(
                            state.userRequest(),
                            state.origin(),
                            state.destination(),
                            state.departureDate(),
                            state.returnDate(),
                            state.travelers(),
                            state.travelStyle(),
                            state.flights().stream().map(flight -> flight.toDisplay()).toList(),
                            state.hotels().stream().map(hotel -> hotel.toDisplay()).toList(),
                            state.research().stream().map(item -> item.toDisplay()).toList(),
                            state.attractions().stream().map(item -> item.toDisplay()).toList(),
                            budget,
                            itinerary,
                            state.validationErrors()));
            if (plan != null && !plan.isBlank()) {
                return plan;
            }
        } catch (Exception exception) {
            log.warn("Final agent LLM failed, using structured fallback", exception);
        }
        return fallback(state, itinerary);
    }

    private String fallback(TravelState state, String itinerary) {
        StringBuilder sb = new StringBuilder();
        sb.append("Trip plan for ").append(state.destination()).append('\n');
        sb.append("From ").append(state.origin()).append(" on ").append(state.departureDate())
                .append(" returning ").append(state.returnDate()).append('\n');
        if (itinerary != null && !itinerary.isBlank()) {
            sb.append('\n').append(itinerary);
        }
        if (!state.validationErrors().isEmpty()) {
            sb.append("\nCaveats: ").append(String.join(" ", state.validationErrors()));
        }
        return sb.toString();
    }
}
