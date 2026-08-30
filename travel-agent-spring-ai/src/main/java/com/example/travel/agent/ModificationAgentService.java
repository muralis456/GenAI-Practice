package com.example.travel.agent;

import com.example.travel.config.TravelModelsProperties.AgentRole;
import com.example.travel.graph.TravelState;
import com.example.travel.model.ModificationRequest;
import com.example.travel.service.RoutedLlm;
import com.example.travel.support.JsonSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class ModificationAgentService {

    private static final Logger log = LoggerFactory.getLogger(ModificationAgentService.class);

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
                            + "\"destination\":\"\",\"days\":null,\"targetRating\":\"\",\"preserveBudget\":true,\"notes\":\"\"}. "
                            + "Do not assume cheaper unless the user asked to save money.",
                    "Current destination=" + state.destination()
                            + " style=" + state.travelStyle()
                            + "\nUser modification: " + notes);
            return jsonSupport.read(content, ModificationRequest.class)
                    .map(parsed -> {
                        if (TravelState.isBlank(parsed.getNotes())) {
                            parsed.setNotes(notes);
                        }
                        return parsed;
                    })
                    .orElse(heuristic);
        } catch (Exception exception) {
            log.warn("Modification LLM failed; using heuristic", exception);
            return heuristic;
        }
    }

    public static ModificationRequest heuristic(String notes) {
        ModificationRequest request = new ModificationRequest();
        request.setNotes(notes == null ? "" : notes);
        String text = request.getNotes().toLowerCase(Locale.ROOT);
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
        if (containsAny(text, "cheaper", "budget", "reduce cost", "lower cost", "save money", "too expensive")) {
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

    private static boolean containsAny(String text, String... needles) {
        for (String needle : needles) {
            if (text.contains(needle)) {
                return true;
            }
        }
        return false;
    }
}
