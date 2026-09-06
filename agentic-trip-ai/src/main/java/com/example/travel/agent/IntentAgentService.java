package com.example.travel.agent;

import com.example.travel.config.TravelModelsProperties.AgentRole;
import com.example.travel.graph.TravelState;
import com.example.travel.model.AgentDecision;
import com.example.travel.model.IntentPlan;
import com.example.travel.service.RoutedLlm;
import com.example.travel.support.IntentClassifier;
import com.example.travel.support.JsonSupport;
import com.example.travel.support.TripRequirementsParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class IntentAgentService {

    private static final Logger log =
            LoggerFactory.getLogger(IntentAgentService.class);

    private final RoutedLlm routedLlm;
    private final JsonSupport jsonSupport;

    public IntentAgentService(
            RoutedLlm routedLlm,
            JsonSupport jsonSupport) {

        this.routedLlm = routedLlm;
        this.jsonSupport = jsonSupport;
    }

    /**
     * Classifies the initial user request.
     *
     * The deterministic classifier is used first.
     * If confidence is below the configured threshold,
     * the LLM is used to refine the intent.
     */
    public Map<String, Object> classify(TravelState state) {

        IntentPlan plan =
                IntentClassifier.classify(state.userRequest());

        if (plan.getConfidence() < IntentClassifier.LLM_THRESHOLD) {
            plan = refineWithLlm(state, plan);
        }

        log.info(
                "Intent agent requestType={} confidence={} for query={}",
                plan.getRequestType(),
                plan.getConfidence(),
                state.userRequest());

        return toUpdates(state, plan);
    }

    /**
     * Classifies ONLY the latest user modification.
     *
     * Important:
     * - Does not use the previous IntentPlan.
     * - Does not use previous specialist requirements.
     * - Does not use previous specialist results.
     * - Does not use validation errors.
     * - Does not use ReplanStrategy.
     *
     * The LLM determines the semantic intent of the latest request.
     * Java then owns graph routing.
     */
    public Map<String, Object> classifyLatestRequest(
            TravelState state) {

        IntentPlan plan =
                classifyLatestRequestPlan(state);

        return toUpdates(state, plan);
    }

    /**
     * Same as classifyLatestRequest(), but returns the structured
     * IntentPlan directly.
     */
    public IntentPlan classifyLatestRequestPlan(
            TravelState state) {

        if (state == null) {
            return emptyPlan();
        }

        String latestRequest = state.userRequest();

        if (TravelState.isBlank(latestRequest)) {
            return emptyPlan();
        }

        try {

            String system = """
                    You are the Intent Agent for a travel planning system.

                    Your ONLY job is to understand the user's LATEST REQUEST
                    and determine which specialist capabilities are requested
                    by that request.

                    Do NOT reason from any previous plan.

                    Do NOT preserve previous specialist requirements.

                    Do NOT use existing trip data.

                    Do NOT use validation errors.

                    Do NOT use previous specialist results.

                    Do NOT infer capabilities merely because they would be useful.

                    Return exactly ONE JSON object and NOTHING else.

                    JSON shape:

                    {
                      "requestType":"",
                      "needsFlights":false,
                      "needsHotels":false,
                      "needsResearch":false,
                      "needsWeather":false,
                      "needsBudget":false,
                      "needsItinerary":false,
                      "strategy":"",
                      "priority":"",
                      "confidence":0.0
                    }

                    Specialist meanings:

                    needsFlights:
                    TRUE only when the user asks for flight information,
                    flight options, airfare, flights, departure/arrival
                    flight details, or flight changes.

                    needsHotels:
                    TRUE only when the user asks for hotels,
                    accommodation, rooms, hotel options,
                    hotel changes, or hotel details.

                    needsResearch:
                    TRUE only when the user asks for destinations,
                    attractions, activities, places, sightseeing,
                    recommendations, or destination research.

                    needsWeather:
                    TRUE only when the user asks about weather,
                    forecast, temperature, rain, or weather conditions.

                    needsBudget:
                    TRUE only when the user explicitly asks for budget,
                    cost, expenses, prices, affordability,
                    cost estimation, or cost breakdown.

                    needsItinerary:
                    TRUE only when the user asks for a day-by-day itinerary,
                    trip schedule, or an itinerary change.

                    IMPORTANT RULES:

                    1. Every needs* field describes ONLY the LATEST USER REQUEST.

                    2. If a capability is not requested by the latest request,
                       that field MUST be false.

                    3. Do NOT copy requirements from an earlier request.

                    4. Do NOT activate a specialist because the information
                       exists in the current trip.

                    5. Do NOT activate budget just because the trip has a budget.

                    6. Do NOT activate hotels just because the trip contains
                       hotel information.

                    7. Do NOT activate flights just because the trip contains
                       flight information.

                    8. Do NOT activate itinerary just because this is a trip.

                    9. Select only capabilities actually requested by the user.

                    10. Multiple fields may be true if the latest request
                        explicitly asks for multiple capabilities.

                    Examples:

                    User:
                    "include flight details from Bangalore to Mumbai"

                    Output:
                    {
                      "requestType":"FLIGHT_DETAILS",
                      "needsFlights":true,
                      "needsHotels":false,
                      "needsResearch":false,
                      "needsWeather":false,
                      "needsBudget":false,
                      "needsItinerary":false,
                      "strategy":"none",
                      "priority":"normal",
                      "confidence":0.95
                    }

                    User:
                    "also show hotel options"

                    Output:
                    {
                      "requestType":"HOTEL_DETAILS",
                      "needsFlights":false,
                      "needsHotels":true,
                      "needsResearch":false,
                      "needsWeather":false,
                      "needsBudget":false,
                      "needsItinerary":false,
                      "strategy":"none",
                      "priority":"normal",
                      "confidence":0.95
                    }

                    User:
                    "give me weather for Mumbai"

                    Output:
                    {
                      "requestType":"WEATHER",
                      "needsFlights":false,
                      "needsHotels":false,
                      "needsResearch":false,
                      "needsWeather":true,
                      "needsBudget":false,
                      "needsItinerary":false,
                      "strategy":"none",
                      "priority":"normal",
                      "confidence":0.95
                    }

                    User:
                    "give me a budget breakdown"

                    Output:
                    {
                      "requestType":"BUDGET",
                      "needsFlights":false,
                      "needsHotels":false,
                      "needsResearch":false,
                      "needsWeather":false,
                      "needsBudget":true,
                      "needsItinerary":false,
                      "strategy":"none",
                      "priority":"normal",
                      "confidence":0.95
                    }

                    User:
                    "show me hotels and flights"

                    Output:
                    {
                      "requestType":"HOTEL_AND_FLIGHT",
                      "needsFlights":true,
                      "needsHotels":true,
                      "needsResearch":false,
                      "needsWeather":false,
                      "needsBudget":false,
                      "needsItinerary":false,
                      "strategy":"none",
                      "priority":"normal",
                      "confidence":0.95
                    }

                    User:
                    "change day 2 itinerary"

                    Output:
                    {
                      "requestType":"ITINERARY_CHANGE",
                      "needsFlights":false,
                      "needsHotels":false,
                      "needsResearch":false,
                      "needsWeather":false,
                      "needsBudget":false,
                      "needsItinerary":true,
                      "strategy":"none",
                      "priority":"normal",
                      "confidence":0.95
                    }

                    User:
                    "find attractions in Mumbai"

                    Output:
                    {
                      "requestType":"RESEARCH",
                      "needsFlights":false,
                      "needsHotels":false,
                      "needsResearch":true,
                      "needsWeather":false,
                      "needsBudget":false,
                      "needsItinerary":false,
                      "strategy":"none",
                      "priority":"normal",
                      "confidence":0.95
                    }

                    User:
                    "give me weather and budget details"

                    Output:
                    {
                      "requestType":"WEATHER_AND_BUDGET",
                      "needsFlights":false,
                      "needsHotels":false,
                      "needsResearch":false,
                      "needsWeather":true,
                      "needsBudget":true,
                      "needsItinerary":false,
                      "strategy":"none",
                      "priority":"normal",
                      "confidence":0.95
                    }

                    Do NOT add a capability merely because it would be useful.
                    """;

            /*
             * IMPORTANT FIX:
             *
             * Do NOT send the current trip context here.
             *
             * The previous implementation sent:
             * origin, destination, dates, travelers, budget,
             * travelStyle, etc.
             *
             * That allowed the small LLM to incorrectly infer
             * needsBudget=true simply because a budget existed
             * in the trip.
             *
             * For modification intent classification, the latest
             * user request is the authoritative semantic input.
             */
            String user = """
                    LATEST USER REQUEST:

                    %s

                    Classify ONLY this request.
                    """.formatted(latestRequest);

            String content =
                    routedLlm.complete(
                            AgentRole.EXTRACT,
                            system,
                            user);

            log.info(
                    "Latest intent raw LLM response={}",
                    content);

            IntentPlan parsed =
                    jsonSupport
                            .read(content, IntentPlan.class)
                            .orElseGet(this::emptyPlan);

            /*
             * Some small models omit confidence.
             * Keep the existing safe fallback.
             */
            if (parsed.getConfidence() <= 0) {
                parsed.setConfidence(0.80);
            }

            log.info(
                    "Latest intent result requestType={} flights={} hotels={} research={} weather={} budget={} itinerary={} confidence={}",
                    parsed.getRequestType(),
                    parsed.isNeedsFlights(),
                    parsed.isNeedsHotels(),
                    parsed.isNeedsResearch(),
                    parsed.isNeedsWeather(),
                    parsed.isNeedsBudget(),
                    parsed.isNeedsItinerary(),
                    parsed.getConfidence());

            return parsed;

        } catch (Exception exception) {

            log.warn(
                    "Latest intent LLM classification failed",
                    exception);

            return emptyPlan();
        }
    }

    /**
     * Converts the IntentPlan into TravelState updates.
     *
     * TravelState.applyIntentAndRun() owns the mapping between
     * intent capabilities and NEEDS/RUN flags.
     */
   private Map<String, Object> toUpdates(
        TravelState state,
        IntentPlan plan) {

    Map<String, Object> updates = new LinkedHashMap<>();

    if (plan == null) {
        plan = emptyPlan();
    }

    updates.put(
            TravelState.REQUEST_TYPE,
            plan.getRequestType());

    /*
     * ============================================================
     * CUMULATIVE NEEDS
     * ============================================================
     *
     * NEEDS_* represents everything requested during the
     * conversation so far.
     *
     * Example:
     *
     * Request 1: hotels
     * Request 2: flights
     * Request 3: weather + budget
     *
     * Final:
     * hotel=true
     * flight=true
     * weather=true
     * budget=true
     */
    boolean previousFlights =
            state != null && state.needsFlights();

    boolean previousHotels =
            state != null && state.needsHotels();

    boolean previousResearch =
            state != null && state.needsResearch();

    boolean previousWeather =
            state != null && state.needsWeather();

    boolean previousBudget =
            state != null && state.needsBudget();

    boolean previousItinerary =
            state != null && state.needsItinerary();

    updates.put(
            TravelState.NEEDS_FLIGHTS,
            previousFlights || plan.isNeedsFlights());

    updates.put(
            TravelState.NEEDS_HOTELS,
            previousHotels || plan.isNeedsHotels());

    updates.put(
            TravelState.NEEDS_RESEARCH,
            previousResearch || plan.isNeedsResearch());

    updates.put(
            TravelState.NEEDS_WEATHER,
            previousWeather || plan.isNeedsWeather());

    updates.put(
            TravelState.NEEDS_BUDGET,
            previousBudget || plan.isNeedsBudget());

    updates.put(
            TravelState.NEEDS_ITINERARY,
            previousItinerary || plan.isNeedsItinerary());


    /*
     * ============================================================
     * CURRENT RUN
     * ============================================================
     *
     * RUN_* represents ONLY what needs to execute now.
     *
     * Therefore if the latest request is:
     *
     * "weather and budget"
     *
     * then:
     *
     * RUN_WEATHER=true
     * RUN_BUDGET=true
     *
     * everything else=false.
     */
    updates.put(
            TravelState.RUN_FLIGHTS,
            plan.isNeedsFlights());

    updates.put(
            TravelState.RUN_HOTELS,
            plan.isNeedsHotels());

    updates.put(
            TravelState.RUN_RESEARCH,
            plan.isNeedsResearch());

    updates.put(
            TravelState.RUN_WEATHER,
            plan.isNeedsWeather());

    updates.put(
            TravelState.RUN_BUDGET,
            plan.isNeedsBudget());

    updates.put(
            TravelState.RUN_ITINERARY,
            plan.isNeedsItinerary());


    /*
     * ============================================================
     * PLAN METADATA
     * ============================================================
     */
    updates.put(
            TravelState.PLAN_STRATEGY,
            plan.getStrategy());

    updates.put(
            TravelState.PLAN_PRIORITY,
            plan.getPriority());

    updates.put(
            TravelState.INTENT_CONFIDENCE,
            plan.getConfidence());


    /*
     * ============================================================
     * TRIP REQUIREMENTS / DECISION
     * ============================================================
     */
    if (state != null) {

        updates.put(
                TravelState.TRIP_REQUIREMENTS,
                TripRequirementsParser.parse(
                        state.userRequest()));

        updates.put(
                TravelState.LAST_DECISION,
                new AgentDecision(
                        "intent",
                        plan.getRequestType(),
                        plan.summary(),
                        plan.getConfidence()));
    }

    return updates;
}

    /**
     * Refines the deterministic initial classification using the LLM.
     *
     * This method is used only for the INITIAL request.
     */
    private IntentPlan refineWithLlm(
            TravelState state,
            IntentPlan fallback) {

        try {

            String content =
                    routedLlm.complete(
                            AgentRole.EXTRACT,
                            """
                            You are the Intent Agent.

                            Return JSON only.

                            Determine the user's current request semantically.

                            Do not copy requirements from any previous plan.

                            Set unrelated specialist fields to false.

                            A capability must be true only when the
                            user's request actually requires that capability.

                            JSON shape:

                            {
                              "requestType":"",
                              "needsFlights":false,
                              "needsHotels":false,
                              "needsResearch":false,
                              "needsWeather":false,
                              "needsBudget":false,
                              "needsItinerary":false,
                              "strategy":"",
                              "priority":"",
                              "confidence":0.0
                            }

                            Specialist rules:

                            - needsFlights=true only for flight requests.
                            - needsHotels=true only for hotel requests.
                            - needsResearch=true only for research,
                              attractions, activities or recommendations.
                            - needsWeather=true only for weather requests.
                            - needsBudget=true only for explicit budget,
                              cost or expense requests.
                            - needsItinerary=true only for itinerary
                              or schedule requests.

                            Do not activate a specialist merely because
                            it would be useful.

                            If the user asks only for flights,
                            needsBudget MUST be false.

                            If the user asks only for hotels,
                            needsBudget MUST be false.

                            If the user asks only for weather,
                            needsBudget MUST be false.
                            """,
                            "User request: "
                                    + state.userRequest()
                                    + "\nHeuristic guess: "
                                    + fallback.summary());

            return jsonSupport
                    .read(content, IntentPlan.class)
                    .map(parsed -> {

                        if (parsed.getConfidence() <= 0) {
                            parsed.setConfidence(0.8);
                        }

                        return finalizeIntent(
                                fallback,
                                parsed);
                    })
                    .orElse(fallback);

        } catch (Exception exception) {

            log.warn(
                    "Intent LLM fallback failed; using deterministic plan",
                    exception);

            return fallback;
        }
    }

    /**
     * Only a genuine TRIP_PLANNING classification expands
     * to the complete planning pipeline.
     *
     * For every other intent, the LLM's explicit booleans
     * are authoritative.
     *
     * There is intentionally NO OR merge with the old plan.
     */
    private IntentPlan finalizeIntent(
            IntentPlan base,
            IntentPlan llm) {

        if (IntentPlan.TRIP_PLANNING.equals(
                base.getRequestType())) {

            llm.setRequestType(
                    IntentPlan.TRIP_PLANNING);

            llm.setNeedsFlights(true);
            llm.setNeedsHotels(true);
            llm.setNeedsResearch(true);
            llm.setNeedsWeather(true);
            llm.setNeedsBudget(true);
            llm.setNeedsItinerary(true);
        }

        return llm;
    }

    /**
     * Returns a safe empty intent.
     *
     * Missing/invalid intent must never accidentally
     * execute a specialist.
     */
    private IntentPlan emptyPlan() {

        IntentPlan plan =
                new IntentPlan();

        plan.setRequestType("GENERAL");

        plan.setNeedsFlights(false);
        plan.setNeedsHotels(false);
        plan.setNeedsResearch(false);
        plan.setNeedsWeather(false);
        plan.setNeedsBudget(false);
        plan.setNeedsItinerary(false);

        plan.setStrategy("none");
        plan.setPriority("none");
        plan.setConfidence(0.0);

        return plan;
    }
}