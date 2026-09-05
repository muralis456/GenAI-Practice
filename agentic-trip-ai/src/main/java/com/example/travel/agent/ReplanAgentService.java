package com.example.travel.agent;

import com.example.travel.config.TravelModelsProperties.AgentRole;
import com.example.travel.graph.NodeFailureRouting;
import com.example.travel.graph.TravelState;
import com.example.travel.model.ModificationRequest;
import com.example.travel.model.ReplanAction;
import com.example.travel.model.ReplanStrategy;
import com.example.travel.service.RoutedLlm;
import com.example.travel.support.JsonSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class ReplanAgentService {

    private static final Logger log = LoggerFactory.getLogger(ReplanAgentService.class);

    private final RoutedLlm routedLlm;
    private final JsonSupport jsonSupport;
    private final ReplanStrategyExecutor replanStrategyExecutor;

    public ReplanAgentService(
            RoutedLlm routedLlm,
            JsonSupport jsonSupport,
            ReplanStrategyExecutor replanStrategyExecutor) {

        this.routedLlm = routedLlm;
        this.jsonSupport = jsonSupport;
        this.replanStrategyExecutor = replanStrategyExecutor;
    }

    public Map<String, Object> decide(TravelState state) {

        /*
         * USER-INITIATED CHANGE
         *
         * The LLM gets the first opportunity to understand the
         * user's latest request.
         */
        if (hasUserModification(state)) {

            ReplanStrategy strategy = askLlm(state);

            if (hasActions(strategy)) {

                log.info(
                        "Replanner LLM decision actions={} reason={} priority={}",
                        strategy.getActions(),
                        strategy.getReason(),
                        strategy.getPriority());

                return replanStrategyExecutor.apply(
                        state,
                        strategy);
            }

            /*
             * If the LLM failed, use the structured modification
             * information as a fallback.
             *
             * This is NOT keyword matching.
             * ModificationRequest already contains structured
             * information extracted earlier in the pipeline.
             */
            ReplanStrategy modificationFallback = fromModification(state.modification());

            if (hasActions(modificationFallback)) {

                log.info(
                        "Replanner using structured modification fallback actions={} reason={}",
                        modificationFallback.getActions(),
                        modificationFallback.getReason());

                return replanStrategyExecutor.apply(
                        state,
                        modificationFallback);
            }
        }

        /*
         * NODE FAILURE RECOVERY
         *
         * Only use NodeFailureRouting when there is an actual
         * failed node.
         */
        if (hasNodeFailure(state)) {

            ReplanStrategy strategy = NodeFailureRouting.replanForFailure(state);

            if (hasActions(strategy)) {

                log.info(
                        "Replanner node-failure recovery actions={} reason={}",
                        strategy.getActions(),
                        strategy.getReason());

                return replanStrategyExecutor.apply(
                        state,
                        strategy);
            }
        }

        /*
         * Last-resort recovery.
         */
        ReplanStrategy strategy = fallback(state);

        log.info(
                "Replanner fallback actions={} reason={}",
                strategy.getActions(),
                strategy.getReason());

        return replanStrategyExecutor.apply(
                state,
                strategy);
    }

    /**
     * Determines whether this replan was initiated by a user request.
     */
    private boolean hasUserModification(TravelState state) {

        if (state == null || state.modification() == null) {
            return false;
        }

        ModificationRequest modification = state.modification();

        return !TravelState.isBlank(
                modification.getNotes());
    }

    /**
     * Determines whether the graph is recovering from an actual
     * node failure.
     */
    private boolean hasNodeFailure(TravelState state) {

        return state != null
                && state.nodeFailure() != null
                && !TravelState.isBlank(
                        state.nodeFailure().getLastFailedNode());
    }

    private boolean hasActions(ReplanStrategy strategy) {

        return strategy != null
                && strategy.getActions() != null
                && !strategy.getActions().isEmpty();
    }

    /**
     * LLM-based semantic replanning.
     *
     * The model receives the current trip, current available data,
     * current needs and the latest user request.
     *
     * There is deliberately NO request.contains("flight") logic here.
     */
    private ReplanStrategy askLlm(TravelState state) {

        try {

            String system = """
                    You are the Replanning Decision Agent for a travel planning system.

                    Your ONLY job is to understand the user's LATEST REQUEST and select
                    the minimum set of specialist actions required to satisfy that request.

                    IMPORTANT:
                    - Use semantic understanding, not keyword matching.
                    - The latest user request has priority over the existing plan.
                    - Existing "needs" flags describe the OLD plan. They do NOT restrict
                      what the user can request now.
                    - Do not interpret every modification as an itinerary change.
                    - Select an action based on WHAT INFORMATION OR CHANGE the user wants.

                    AVAILABLE ACTIONS:

                    GET_FLIGHT_DETAILS
                    Use when the user wants flight information, flight options,
                    flight details, flights between two cities, or wants flights
                    included in the response.

                    CHEAPER_FLIGHT
                    Use ONLY when the user explicitly wants cheaper/lowest-cost flights
                    or wants flight cost reduced.

                    REDUCE_HOTEL_BUDGET
                    Use when the user wants cheaper hotels or wants hotel cost reduced.

                    HOTEL_UPGRADE
                    Use when the user wants a better, premium, upgraded, or luxury hotel.

                    REMOVE_EXPENSIVE_ATTRACTIONS
                    Use when the user wants expensive attractions removed or reduced.

                    ADD_DESTINATION
                    Use when the user explicitly wants another destination added.

                    ADJUST_ITINERARY
                    Use ONLY when the user explicitly wants the itinerary/schedule/
                    day-by-day plan changed or rebuilt.

                    DECISION RULES:

                    1. If the user asks for flight details or wants flights included,
                       choose GET_FLIGHT_DETAILS.

                    2. If the user asks for cheaper flights, choose CHEAPER_FLIGHT.
                       Do not choose GET_FLIGHT_DETAILS in this case.

                    3. If the user asks for hotel changes, choose the appropriate
                       hotel action.

                    4. If the user asks for an itinerary/day-plan change, choose
                       ADJUST_ITINERARY.

                    5. If a request contains both an itinerary change AND a flight
                       request, select BOTH actions.

                    6. If a request says "also include flight details", the required
                       action is GET_FLIGHT_DETAILS even if the existing itinerary
                       is already complete.

                    7. Never choose ADJUST_ITINERARY merely because the user says
                       "modify", "modification", "update", "change", or "also include".

                    EXAMPLES:

                    User:
                    "show me flight details from Bangalore to Mumbai"

                    Actions:
                    ["GET_FLIGHT_DETAILS"]

                    User:
                    "also include flight details from Bangalore to Mumbai"

                    Actions:
                    ["GET_FLIGHT_DETAILS"]

                    User:
                    "modify the trip and also include flight details from Bangalore to Mumbai"

                    Actions:
                    ["ADJUST_ITINERARY", "GET_FLIGHT_DETAILS"]

                    User:
                    "find cheaper flights from Bangalore to Mumbai"

                    Actions:
                    ["CHEAPER_FLIGHT"]

                    User:
                    "change the day 2 itinerary"

                    Actions:
                    ["ADJUST_ITINERARY"]

                    User:
                    "make the hotel cheaper"

                    Actions:
                    ["REDUCE_HOTEL_BUDGET"]

                    User:
                    "upgrade my hotel"

                    Actions:
                    ["HOTEL_UPGRADE"]

                    IMPORTANT:
                    The examples are semantic examples. Do not match words mechanically.
                    Understand the intent of the latest request.

                    Return JSON ONLY.

                    {
                      "reason": "",
                      "targetReduction": 0,
                      "expectedImpact": 0.0,
                      "actions": [],
                      "priority": ""
                    }

                    Allowed actions:
                    GET_FLIGHT_DETAILS
                    CHEAPER_FLIGHT
                    REDUCE_HOTEL_BUDGET
                    REMOVE_EXPENSIVE_ATTRACTIONS
                    HOTEL_UPGRADE
                    ADJUST_ITINERARY
                    ADD_DESTINATION

                    Choose the smallest set of actions required to satisfy the
                    user's latest request.
                    """;

            String user = buildReplanContext(state);

            log.info("REPLAN USER REQUEST = [{}]", state.userRequest());

            log.info(
                    "REPLAN MODIFICATION = type=[{}], notes=[{}]",
                    state.modification() == null
                            ? ""
                            : state.modification().getChangeType(),
                    state.modification() == null
                            ? ""
                            : state.modification().getNotes());

            log.info("REPLAN CONTEXT = {}", user);

            String content = routedLlm.complete(
                    AgentRole.PLANNER,
                    system,
                    user);

            log.info("Replanner raw LLM response={}", content);

            ReplanStrategy strategy = jsonSupport.read(
                    content,
                    ReplanStrategy.class)
                    .orElseGet(ReplanStrategy::new);

            log.info(
                    "Replanner LLM result actions={} reason={} priority={}",
                    strategy.getActions(),
                    strategy.getReason(),
                    strategy.getPriority());

            return strategy;

        } catch (Exception exception) {

            log.warn(
                    "Replanner LLM failed",
                    exception);

            return new ReplanStrategy();
        }
    }

    private String buildReplanContext(TravelState state) {

        return """
                USER'S LATEST REQUEST
                ====================
                %s

                IMPORTANT:
                This is the request you must satisfy now.
                Ignore the fact that the existing plan may already contain
                hotels, flights, itinerary, research, or other information.
                Determine what the user is asking for NOW.

                CURRENT TRIP CONTEXT
                ====================
                Origin: %s
                Destination: %s
                Departure date: %s
                Return date: %s
                Travelers: %s
                Budget: %s
                Travel style: %s

                EXISTING PLAN DATA
                ==================
                Flights available: %s
                Hotels available: %s
                Research available: %s
                Weather available: %s
                Itinerary available: %s

                EXISTING REQUIREMENTS
                =====================
                Flights needed: %s
                Hotels needed: %s
                Research needed: %s
                Weather needed: %s
                Itinerary needed: %s
                Budget needed: %s

                EXISTING MODIFICATION METADATA
                ==============================
                Change type: %s
                Notes: %s

                EXISTING VALIDATION
                ===================
                Validation errors: %s
                Semantic notes: %s
                Over budget: %s
                """.formatted(

                state.userRequest(),

                state.origin(),
                state.destination(),
                state.departureDate(),
                state.returnDate(),
                state.travelers(),
                state.budgetLabel(),
                state.travelStyle(),

                state.flights() != null && !state.flights().isEmpty(),
                state.hotels() != null && !state.hotels().isEmpty(),
                state.research() != null && !state.research().isEmpty(),
                state.weather() != null,
                state.itinerary() != null,

                state.needsFlights(),
                state.needsHotels(),
                state.needsResearch(),
                state.needsWeather(),
                state.needsItinerary(),
                state.needsBudget(),

                state.modification() == null
                        ? ""
                        : state.modification().getChangeType(),

                state.modification() == null
                        ? ""
                        : state.modification().getNotes(),

                state.validationErrors(),
                state.semanticNotes(),
                state.overBudget());
    }

    /**
     * Structured fallback for cases where the LLM is unavailable.
     *
     * This does not inspect the natural-language request.
     * It only uses already extracted structured modification fields.
     */
    private ReplanStrategy fromModification(
            ModificationRequest modification) {

        ReplanStrategy strategy = new ReplanStrategy();

        if (modification == null
                || TravelState.isBlank(
                        modification.getChangeType())
                || ModificationRequest.GENERAL.equalsIgnoreCase(
                        modification.getChangeType())) {

            return strategy;
        }

        strategy.setReason(
                modification.getChangeType()
                        + ": "
                        + modification.getNotes());

        List<String> actions = new ArrayList<>();

        if (modification.isReduceCost()) {

            if (modification.isHotelCostReduction()) {

                strategy.setPriority("hotel");

                actions.add(
                        ReplanAction.REDUCE_HOTEL_BUDGET
                                .wireName());

            } else if (modification.isFlightCostReduction()) {

                strategy.setPriority("flight");

                actions.add(
                        ReplanAction.CHEAPER_FLIGHT
                                .wireName());

            } else {

                strategy.setPriority("hotel");

                actions.add(
                        ReplanAction.REDUCE_HOTEL_BUDGET
                                .wireName());

                actions.add(
                        ReplanAction.CHEAPER_FLIGHT
                                .wireName());
            }

        } else if (modification.isHotelUpgrade()) {

            strategy.setPriority("hotel");

            actions.add(
                    ReplanAction.HOTEL_UPGRADE
                            .wireName());

        } else if (modification.isAddDestination()) {

            strategy.setPriority("research");

            actions.add(
                    ReplanAction.ADD_DESTINATION
                            .wireName());

        } else {

            /*
             * This fallback is only used when structured modification
             * data says that the request is an itinerary change.
             *
             * Normal user modifications go through the LLM first.
             */
            strategy.setPriority("itinerary");

            actions.add(
                    ReplanAction.ADJUST_ITINERARY
                            .wireName());
        }

        strategy.setActions(actions);

        return strategy;
    }

    private ReplanStrategy fallback(TravelState state) {

        ReplanStrategy strategy = new ReplanStrategy();

        if (state.overBudget()) {

            strategy.setReason(
                    "budget_exceeded");

            strategy.setPriority(
                    "hotel");

            strategy.setActions(
                    new ArrayList<>(
                            List.of(
                                    ReplanAction.REDUCE_HOTEL_BUDGET
                                            .wireName(),
                                    ReplanAction.CHEAPER_FLIGHT
                                            .wireName())));

        } else if (!state.semanticNotes().isEmpty()) {

            strategy.setReason(
                    "semantic_mismatch");

            strategy.setPriority(
                    "itinerary");

            strategy.setActions(
                    new ArrayList<>(
                            List.of(
                                    ReplanAction.ADJUST_ITINERARY
                                            .wireName())));

        } else {

            strategy.setReason(
                    "validation_failed");

            strategy.setPriority(
                    "attractions");

            strategy.setActions(
                    new ArrayList<>(
                            List.of(
                                    ReplanAction.REMOVE_EXPENSIVE_ATTRACTIONS
                                            .wireName())));
        }

        if (state.budgetSummary() != null
                && state.budgetSummary().getEstimatedCost() != null
                && state.budget() != null) {

            BigDecimal estimated = state.budgetSummary()
                    .getEstimatedCost();

            BigDecimal budget = state.budget();

            strategy.setTargetReduction(
                    estimated
                            .subtract(budget)
                            .doubleValue());
        }

        return strategy;
    }
}