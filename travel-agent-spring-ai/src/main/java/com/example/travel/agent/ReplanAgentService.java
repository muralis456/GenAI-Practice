package com.example.travel.agent;

import com.example.travel.config.TravelModelsProperties.AgentRole;
import com.example.travel.graph.TravelState;
import com.example.travel.model.ModificationRequest;
import com.example.travel.model.ReplanStrategy;
import com.example.travel.service.RoutedLlm;
import com.example.travel.support.JsonSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class ReplanAgentService {

    private static final Logger log = LoggerFactory.getLogger(ReplanAgentService.class);

    private final RoutedLlm routedLlm;
    private final JsonSupport jsonSupport;
    private final ReplanStrategyExecutor replanStrategyExecutor;

    public ReplanAgentService(RoutedLlm routedLlm,
                              JsonSupport jsonSupport,
                              ReplanStrategyExecutor replanStrategyExecutor) {
        this.routedLlm = routedLlm;
        this.jsonSupport = jsonSupport;
        this.replanStrategyExecutor = replanStrategyExecutor;
    }

    public Map<String, Object> decide(TravelState state) {
        ReplanStrategy strategy = fromModification(state.modification());
        if (strategy.getActions() == null || strategy.getActions().isEmpty()) {
            strategy = askLlm(state);
        }
        if (strategy.getActions() == null || strategy.getActions().isEmpty()) {
            strategy = fallback(state);
        }
        log.info("Replanner actions={} reason={}", strategy.getActions(), strategy.getReason());
        return replanStrategyExecutor.apply(state, strategy);
    }

    private ReplanStrategy fromModification(ModificationRequest modification) {
        ReplanStrategy strategy = new ReplanStrategy();
        if (modification == null || TravelState.isBlank(modification.getChangeType())
                || ModificationRequest.GENERAL.equalsIgnoreCase(modification.getChangeType())) {
            return strategy;
        }
        strategy.setReason(modification.getChangeType() + ": " + modification.getNotes());
        List<String> actions = new ArrayList<>();
        if (modification.isReduceCost()) {
            strategy.setPriority("hotel");
            actions.add("reduce_hotel_budget");
            actions.add("cheaper_flight");
        } else if (modification.isHotelUpgrade()) {
            strategy.setPriority("hotel");
            actions.add("hotel_upgrade");
        } else if (modification.isAddDestination()) {
            strategy.setPriority("research");
            actions.add("add_destination");
        } else {
            strategy.setPriority("itinerary");
            actions.add("adjust_itinerary");
        }
        strategy.setActions(actions);
        return strategy;
    }

    private ReplanStrategy askLlm(TravelState state) {
        try {
            String content = routedLlm.complete(AgentRole.PLANNER,
                    "You are the Replanner Agent. Analyze why the plan failed and return JSON only: "
                            + "{\"reason\":\"budget_exceeded|invalid_itinerary|semantic_mismatch|other\","
                            + "\"targetReduction\":0,\"actions\":[\"reduce_hotel_budget\"],"
                            + "\"priority\":\"hotel|flight|attractions|itinerary\"}. "
                            + "Pick actions from: reduce_hotel_budget, cheaper_flight, "
                            + "remove_expensive_attractions, hotel_upgrade, adjust_itinerary. "
                            + "Do not always pick cheaper options — match the failure.",
                    "Validation: " + state.validationErrors()
                            + "\nSemantic: " + state.semanticNotes()
                            + "\nOverBudget=" + state.overBudget()
                            + "\nBudget=" + state.budgetSummary()
                            + "\nHotels=" + state.hotels().size()
                            + "\nFlights=" + state.flights().size()
                            + "\nModification=" + state.modification().getChangeType()
                            + "\nUser=" + state.userRequest());
            return jsonSupport.read(content, ReplanStrategy.class).orElseGet(ReplanStrategy::new);
        } catch (Exception exception) {
            log.warn("Replanner LLM failed; using rule fallback", exception);
            return fallback(state);
        }
    }

    private ReplanStrategy fallback(TravelState state) {
        ReplanStrategy strategy = new ReplanStrategy();
        if (state.overBudget()) {
            strategy.setReason("budget_exceeded");
            strategy.setPriority("hotel");
            strategy.setActions(new ArrayList<>(List.of("reduce_hotel_budget", "cheaper_flight")));
        } else if (!state.semanticNotes().isEmpty()) {
            strategy.setReason("semantic_mismatch");
            strategy.setPriority("itinerary");
            strategy.setActions(new ArrayList<>(List.of("adjust_itinerary")));
        } else {
            strategy.setReason("validation_failed");
            strategy.setPriority("attractions");
            strategy.setActions(new ArrayList<>(List.of("remove_expensive_attractions")));
        }
        if (state.budgetSummary() != null && state.budgetSummary().getEstimatedCost() != null
                && state.budget() != null) {
            strategy.setTargetReduction(state.budgetSummary().getEstimatedCost()
                    .subtract(state.budget()).doubleValue());
        }
        return strategy;
    }
}
