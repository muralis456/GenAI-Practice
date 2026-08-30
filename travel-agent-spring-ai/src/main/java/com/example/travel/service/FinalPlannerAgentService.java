package com.example.travel.service;

import com.example.travel.config.TravelModelsProperties.AgentRole;
import com.example.travel.graph.TravelState;
import com.example.travel.model.BudgetLineItem;
import com.example.travel.model.BudgetSummary;
import com.example.travel.model.FlightOption;
import com.example.travel.model.HotelOption;
import com.example.travel.model.ItineraryDay;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class FinalPlannerAgentService {

    private static final Logger log = LoggerFactory.getLogger(FinalPlannerAgentService.class);

    private final RoutedLlm routedLlm;

    public FinalPlannerAgentService(RoutedLlm routedLlm) {
        this.routedLlm = routedLlm;
    }

    /**
     * Final Agent after Validator: facts are rendered from TravelState (no hallucination),
     * then a short Tips section is optionally polished by the LLM.
     */
    public String compose(TravelState state) {
        log.info("Final agent composing validated report for destination={}", state.destination());
        String factual = buildFactualReport(state);
        String tips = polishTips(state);
        return factual + "\n\n**Tips**\n" + tips;
    }

    private String buildFactualReport(TravelState state) {
        StringBuilder sb = new StringBuilder();
        sb.append("**Final Trip Report: ").append(state.origin()).append(" to ")
                .append(state.destination()).append("**\n\n");

        sb.append("**Overview**\n");
        sb.append(state.origin()).append(" (").append(state.originIata()).append(") → ")
                .append(state.destination()).append(" (").append(state.destinationIata()).append(")\n");
        sb.append("Dates: ").append(state.departureDate()).append(" → ").append(state.returnDate())
                .append(" (").append(state.nights()).append(" nights)\n");
        sb.append("Travelers: ").append(state.travelers())
                .append(" · Style: ").append(state.travelStyle()).append('\n');
        if (!TravelState.isBlank(state.userRequest())) {
            sb.append("Request: ").append(state.userRequest()).append('\n');
        }

        sb.append("\n**Flights**\n");
        boolean flightsUnavailable = state.flights().isEmpty()
                || state.flights().stream().allMatch(f -> "unavailable".equalsIgnoreCase(f.getStatus()));
        if (flightsUnavailable) {
            sb.append("No reliable flight options were returned for this route/date.\n");
        } else {
            for (FlightOption flight : state.flights()) {
                if ("unavailable".equalsIgnoreCase(nullToEmpty(flight.getStatus()))) {
                    continue;
                }
                sb.append("- ").append(flight.toDisplay()).append('\n');
            }
        }

        sb.append("\n**Hotels**\n");
        if (state.hotels().isEmpty()) {
            sb.append("No hotel options extracted.\n");
        } else {
            for (HotelOption hotel : state.hotels()) {
                sb.append("- ").append(hotel.toDisplay()).append('\n');
            }
        }

        sb.append("\n**Day-by-day itinerary**\n");
        if (state.itinerary() != null && state.itinerary().getDays() != null) {
            for (ItineraryDay day : state.itinerary().getDays()) {
                sb.append("Day ").append(day.getDay());
                if (!TravelState.isBlank(day.getTitle())) {
                    sb.append(" — ").append(day.getTitle());
                }
                sb.append('\n');
                if (!TravelState.isBlank(day.getActivities())) {
                    sb.append(day.getActivities()).append('\n');
                }
            }
        }

        sb.append("\n**Budget**\n");
        BudgetSummary budget = state.budgetSummary();
        if (budget != null) {
            for (BudgetLineItem item : budget.getLineItems()) {
                sb.append("- ").append(item.getCategory()).append(": ₹").append(item.getAmountInr()).append('\n');
            }
            if (budget.getEstimatedCost() != null) {
                sb.append("Total estimated: ₹").append(budget.getEstimatedCost()).append('\n');
            }
            if (budget.getRemaining() != null) {
                sb.append("Remaining vs ceiling: ₹").append(budget.getRemaining()).append('\n');
            }
            if (!TravelState.isBlank(budget.getAssessment())) {
                sb.append(budget.getAssessment()).append('\n');
            }
        } else {
            sb.append(state.budgetLabel()).append('\n');
        }

        if (state.weather() != null) {
            sb.append("\n**Weather**\n").append(state.weather().toDisplay()).append('\n');
        }
        return sb.toString().trim();
    }

    private String polishTips(TravelState state) {
        try {
            String tips = routedLlm.complete(AgentRole.FINAL,
                    "Write 3-5 short practical travel tips only. Do not mention flights, hotels, or prices. "
                            + "Do not invent attractions not listed. No JSON.",
                    """
                            Destination: %s
                            Style: %s
                            Weather: %s
                            Attractions: %s
                            Research: %s
                            """.formatted(
                            state.destination(),
                            state.travelStyle(),
                            state.weather() == null ? "" : state.weather().toDisplay(),
                            state.attractions().stream().map(a -> a.toDisplay()).toList(),
                            state.research().stream().map(r -> r.toDisplay()).limit(4).toList()));
            if (tips != null && !tips.isBlank()) {
                return tips.trim();
            }
        } catch (Exception exception) {
            log.warn("Final tips LLM failed; using default tips", exception);
        }
        return defaultTips(state);
    }

    private String defaultTips(TravelState state) {
        StringBuilder sb = new StringBuilder();
        if (state.weather() != null && state.weather().isRainLikely()) {
            sb.append("- Pack a light rain jacket; keep indoor museum/cafe backups for wet hours.\n");
        }
        sb.append("- Keep some cash/card ready for transit and casual meals.\n");
        sb.append("- Confirm hotel check-in time after landing at ")
                .append(TravelState.firstNonBlank(state.destinationIata(), "the airport"))
                .append(".\n");
        return sb.toString().trim();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
