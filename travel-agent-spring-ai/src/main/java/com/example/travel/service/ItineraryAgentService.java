package com.example.travel.service;

import com.example.travel.config.TravelModelsProperties.AgentRole;
import com.example.travel.graph.TravelState;
import com.example.travel.model.Itinerary;
import com.example.travel.support.ItinerarySupport;
import com.example.travel.support.JsonSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class ItineraryAgentService {

    private static final Logger log = LoggerFactory.getLogger(ItineraryAgentService.class);

    private final RoutedLlm routedLlm;
    private final JsonSupport jsonSupport;

    public ItineraryAgentService(RoutedLlm routedLlm, JsonSupport jsonSupport) {
        this.routedLlm = routedLlm;
        this.jsonSupport = jsonSupport;
    }

    public Itinerary build(TravelState state) {
        long nights = state.nights();
        int expectedDays = ItinerarySupport.expectedDays(nights);
        log.info("Itinerary agent building plan for destination={} nights={} expectedDays={}",
                state.destination(), nights, expectedDays);

        LocalDate departure = state.departureDate();
        LocalDate returning = state.returnDate();
        String content;
        try {
            content = routedLlm.complete(AgentRole.ITINERARY,
                    "You are the Itinerary Agent. Return JSON only with shape "
                            + "{\"summary\":\"\",\"days\":[{\"day\":1,\"title\":\"\",\"activities\":\"morning... afternoon...\"}]}. "
                            + "CRITICAL: days MUST contain exactly " + expectedDays + " objects (day 1.." + expectedDays + "). "
                            + "Day 1 MUST mention arrival/check-in. Day " + expectedDays + " MUST mention departure/checkout. "
                            + "If rain is likely, move outdoor activities to drier days. "
                            + "Use flights, hotels, attractions, research, weather, and budget from shared state. "
                            + "Do not invent flight numbers.",
                    stateSnapshot(state, departure, returning, expectedDays));
        } catch (Exception exception) {
            log.warn("Itinerary LLM failed for destination={}", state.destination(), exception);
            return ItinerarySupport.skeleton(nights, state.destination(), state.attractions());
        }

        Itinerary parsed = jsonSupport.read(content, Itinerary.class)
                .filter(itinerary -> !itinerary.isEmpty())
                .orElseGet(() -> ItinerarySupport.skeleton(nights, state.destination(), state.attractions()));
        Itinerary normalized = ItinerarySupport.normalize(parsed, nights, state.destination(), state.attractions());
        log.info("Itinerary normalized to {} day(s)",
                normalized.getDays() == null ? 0 : normalized.getDays().size());
        return normalized;
    }

    private String stateSnapshot(TravelState state, LocalDate departure, LocalDate returning, int expectedDays) {
        String budgetNotes = state.budgetSummary() == null ? "" : state.budgetSummary().toDisplay();
        return """
                Destination=%s
                Origin=%s
                Dates=%s to %s
                Required days=%s (nights=%s)
                Travelers=%s
                Style=%s
                Budget=%s
                Replan guidance=%s
                Weather=%s
                Flights=%s
                Hotels=%s
                Attractions=%s
                Research=%s
                Budget summary=%s
                """.formatted(
                state.destination(),
                state.origin(),
                departure,
                returning,
                expectedDays,
                state.nights(),
                state.travelers(),
                state.travelStyle(),
                state.budgetLabel(),
                state.replanGuidance(),
                state.weather() == null ? "" : state.weather().toDisplay(),
                state.flights().stream().map(flight -> flight.toDisplay()).toList(),
                state.hotels().stream().map(hotel -> hotel.toDisplay()).toList(),
                state.attractions().stream().map(attraction -> attraction.toDisplay()).toList(),
                state.research().stream().map(item -> item.toDisplay()).toList(),
                budgetNotes
        );
    }
}
