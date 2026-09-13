package com.example.travel.agent;

import com.example.travel.config.TravelModelsProperties.AgentRole;
import com.example.travel.graph.TravelState;
import com.example.travel.model.AgentDecision;
import com.example.travel.model.IntentPlan;
import com.example.travel.service.RoutedLlm;
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
        IntentPlan plan = classifySemantically(state);

        log.info(
                "Intent agent requestType={} confidence={} capabilities=[flights={},hotels={},research={},weather={},budget={},itinerary={},knowledge={}] query={}",
                plan.getRequestType(), plan.getConfidence(),
                plan.isNeedsFlights(), plan.isNeedsHotels(), plan.isNeedsResearch(),
                plan.isNeedsWeather(), plan.isNeedsBudget(), plan.isNeedsItinerary(),
                plan.isNeedsKnowledge(), state == null ? "" : state.userRequest());

        return toUpdates(state, plan);
    }

    /**
     * Semantic-first intent analysis. There is deliberately no keyword
     * classifier in the decision path. The model interprets the user's
     * meaning, including natural language noise, typos, implicit objectives
     * and multiple simultaneous requests. Java only applies safety rules after
     * the model response: no invented capabilities and no invented intent.
     */
    /**
     * Production semantic intent pipeline.
     *
     * IMPORTANT: this method intentionally contains NO lexical/regex intent
     * detection. User language is interpreted by the semantic model. Java only
     * validates the returned structure and derives routing metadata from the
     * capability vector. This prevents every new phrasing from becoming another
     * keyword patch.
     */
    private IntentPlan classifySemantically(TravelState state) {
        if (state == null || TravelState.isBlank(state.userRequest())) {
            return emptyPlan();
        }

        String request = state.userRequest();
        IntentPlan primary = runSemanticIntentPass(request, false);
        primary = sanitizeSemanticPlan(primary);

        log.info("Intent primary semantic result request={} type={} confidence={} capabilities={}",
                request, primary.getRequestType(), primary.getConfidence(), capabilitySummary(primary));

        // A good semantic classification is authoritative. Do NOT let an
        // independent embedding classifier veto a valid multi-capability plan.
        // The previous architecture caused exactly this regression: the LLM
        // correctly detected flights/budget/itinerary, then embedding similarity
        // returned all false and the request became GENERAL.
        if (hasAnyCapability(primary) && primary.getConfidence() >= 0.55) {
            return finalizeSemanticPlan(primary);
        }

        // If the first model is uncertain or returns an empty capability vector,
        // ask a second semantic pass to independently reconsider the same text.
        // This is still meaning-based and works for new wording, typos and
        // natural language that was never anticipated by Java code.
        IntentPlan adjudicated = runSemanticIntentPass(request, true);
        adjudicated = sanitizeSemanticPlan(adjudicated);

        log.info("Intent secondary semantic result request={} type={} confidence={} capabilities={}",
                request, adjudicated.getRequestType(), adjudicated.getConfidence(), capabilitySummary(adjudicated));

        if (hasAnyCapability(adjudicated)
                && (!hasAnyCapability(primary) || adjudicated.getConfidence() >= primary.getConfidence())) {
            return finalizeSemanticPlan(adjudicated);
        }

        if (hasAnyCapability(primary)) {
            return finalizeSemanticPlan(primary);
        }

        // Embeddings remain an observability/recovery signal only. They are not
        // allowed to overwrite a semantic LLM decision or manufacture a set of
        // capabilities. If both semantic passes are genuinely uncertain, the
        // safe result is GENERAL rather than an invented specialist action.
        log.info("Intent semantic classification unresolved request={}; returning GENERAL", request);
        return finalizeSemanticPlan(emptyPlan());
    }

    private IntentPlan runSemanticIntentPass(String request, boolean adjudication) {
        String system = adjudication ? """
                You are the independent semantic adjudicator for a production travel-agent.

                Re-evaluate the USER REQUEST from its meaning, not literal words or
                predefined phrases. The wording may be novel, abbreviated, noisy,
                misspelled, conversational, or grammatically incomplete.

                Identify every capability the user is actually requesting NOW.
                Do not infer a capability merely because it would be useful.
                Do not use previous conversation state.

                Capability meanings:
                flights = airline/airfare/flight search or flight details.
                hotels = accommodation/lodging/rooms/stay options.
                research = recommendations, attractions, activities, destination research
                           or information that needs current external research.
                weather = current/forecast weather or weather-dependent conditions.
                budget = calculating, estimating, comparing, constraining or optimizing
                         travel cost/spend.
                itinerary = organizing a journey into a coherent schedule or day-by-day plan,
                            including a request to create or modify that schedule.
                knowledge = durable/general travel guidance such as culture, customs, safety,
                            packing, visa guidance, local practical advice or overview. It can be
                            combined with a live capability when the user explicitly wants practical
                            travel guidance derived from that information; do not infer it just from
                            the destination or from a generic travel mention.

                Key semantic rule: infer the user's objective, not the presence or absence
                of a particular word. For example, a person can clearly ask for a vacation
                schedule without using the word "itinerary", and can ask for cost limits
                without using the word "budget".

                Preserve multiple objectives when the user asks for them. Do not collapse
                a multi-objective request to one specialist.

                Return JSON only with this exact shape:
                {
                  "requestType":"",
                  "needsFlights":false,
                  "needsHotels":false,
                  "needsResearch":false,
                  "needsWeather":false,
                  "needsBudget":false,
                  "needsItinerary":false,
                  "needsKnowledge":false,
                  "strategy":"",
                  "priority":"",
                  "confidence":0.0
                }
                """ : """
                You are the semantic intent planner for a production travel-agent system.

                Understand the USER REQUEST by meaning. Do not classify by exact keywords,
                regex patterns, or a fixed list of trigger phrases. Users may express the
                same goal in completely different ways, use typos, abbreviations, speech-to-
                text errors, slang, or incomplete grammar.

                Determine every capability explicitly or semantically requested by the user.
                Do not activate capabilities merely because they might be useful.
                Do not copy capabilities from previous state.

                Capability meanings:
                - flights: airline/airfare/flight search, options, availability or details
                - hotels: accommodation/lodging/rooms/stay options
                - research: recommendations, attractions, activities or current destination research
                - weather: current/forecast weather, temperature, precipitation or conditions
                - budget: travel cost estimation, comparison, constraints or optimization
                - itinerary: a coherent trip schedule, day-by-day journey plan, or schedule change
                - knowledge: durable travel guidance such as culture, customs, safety, packing,
                  visa guidance, practical local advice or destination overview. It may be
                  requested alongside a live capability when the user is asking for the travel
                  implications of that information (for example weather for an upcoming visit,
                  or flight/hotel advice that explicitly asks for practical travel guidance).
                  Do not enable it merely because the request happens to mention a destination.

                Semantic principles:
                - Infer intent from the complete sentence and relationships between its parts.
                - A route plus a duration plus a travel objective can express trip planning even
                  when the user never says "plan" or "itinerary".
                - A monetary constraint can express a budget objective even when the user never
                  says "budget".
                - Multiple requested outcomes must remain multiple capabilities.
                - Do not add flights, hotels, weather, research or budget simply because a trip exists.
                - Do not confuse durable knowledge with live research.
                - If the request is genuinely ambiguous, return GENERAL with all capabilities false.

                Return JSON only:
                {
                  "requestType":"",
                  "needsFlights":false,
                  "needsHotels":false,
                  "needsResearch":false,
                  "needsWeather":false,
                  "needsBudget":false,
                  "needsItinerary":false,
                  "needsKnowledge":false,
                  "strategy":"",
                  "priority":"",
                  "confidence":0.0
                }
                """;

        try {
            String content = routedLlm.complete(
                    AgentRole.EXTRACT,
                    system,
                    "USER REQUEST:\n" + request + "\n\nReturn the semantic capability plan now.");
            return jsonSupport.read(content, IntentPlan.class).orElseGet(this::emptyPlan);
        } catch (Exception ex) {
            log.warn("Semantic intent pass failed adjudication={}", adjudication, ex);
            return emptyPlan();
        }
    }

    private boolean hasAnyCapability(IntentPlan plan) {
        return plan != null && (plan.isNeedsFlights() || plan.isNeedsHotels()
                || plan.isNeedsResearch() || plan.isNeedsWeather()
                || plan.isNeedsBudget() || plan.isNeedsItinerary()
                || plan.isNeedsKnowledge());
    }

    private IntentPlan finalizeSemanticPlan(IntentPlan plan) {
        IntentPlan result = plan == null ? emptyPlan() : plan;
        int count = (result.isNeedsFlights() ? 1 : 0)
                + (result.isNeedsHotels() ? 1 : 0)
                + (result.isNeedsResearch() ? 1 : 0)
                + (result.isNeedsWeather() ? 1 : 0)
                + (result.isNeedsBudget() ? 1 : 0)
                + (result.isNeedsItinerary() ? 1 : 0)
                + (result.isNeedsKnowledge() ? 1 : 0);

        if (count == 0) {
            result.setRequestType("GENERAL");
            result.setStrategy("none");
            result.setPriority("none");
            return result;
        }

        // Request type is derived from the semantic capability vector only.
        // No request wording is inspected here.
        if (result.isNeedsItinerary()) {
            result.setRequestType(IntentPlan.TRIP_PLANNING);
        } else if (count > 1) {
            result.setRequestType("MULTI_CAPABILITY");
        } else if (result.isNeedsFlights()) {
            result.setRequestType(IntentPlan.FLIGHT_SEARCH);
        } else if (result.isNeedsHotels()) {
            result.setRequestType(IntentPlan.HOTEL_SEARCH);
        } else if (result.isNeedsWeather()) {
            result.setRequestType(IntentPlan.WEATHER);
        } else if (result.isNeedsBudget()) {
            result.setRequestType("BUDGET");
        } else if (result.isNeedsResearch()) {
            result.setRequestType(IntentPlan.RESEARCH);
        } else {
            result.setRequestType("TRAVEL_INFORMATION");
        }

        result.setStrategy(count > 1 ? "multi_capability" : result.getStrategy());
        if (result.getStrategy() == null || result.getStrategy().isBlank() || "none".equalsIgnoreCase(result.getStrategy())) {
            result.setStrategy(result.isNeedsItinerary() ? "trip_planning"
                    : result.isNeedsFlights() ? "flight_only"
                    : result.isNeedsHotels() ? "hotel_only"
                    : result.isNeedsWeather() ? "weather_only"
                    : result.isNeedsBudget() ? "budget_only"
                    : result.isNeedsResearch() ? "research_only"
                    : "rag_only");
        }
        if (result.getPriority() == null || result.getPriority().isBlank() || "none".equalsIgnoreCase(result.getPriority())) {
            result.setPriority(result.isNeedsItinerary() ? "itinerary"
                    : result.isNeedsFlights() ? "flights"
                    : result.isNeedsHotels() ? "hotels"
                    : result.isNeedsWeather() ? "weather"
                    : result.isNeedsBudget() ? "budget"
                    : result.isNeedsResearch() ? "research"
                    : "knowledge");
        }
        return result;
    }

    private IntentPlan sanitizeSemanticPlan(IntentPlan plan) {
        if (plan == null) return emptyPlan();
        if (plan.getConfidence() <= 0) plan.setConfidence(0.5);
        if (plan.getConfidence() > 1) plan.setConfidence(1);
        if (plan.getRequestType() == null || plan.getRequestType().isBlank()) {
            plan.setRequestType("GENERAL");
        }
        return plan;
    }

    private String capabilitySummary(IntentPlan plan) {
        return "[flights=" + plan.isNeedsFlights()
                + ",hotels=" + plan.isNeedsHotels()
                + ",research=" + plan.isNeedsResearch()
                + ",weather=" + plan.isNeedsWeather()
                + ",budget=" + plan.isNeedsBudget()
                + ",itinerary=" + plan.isNeedsItinerary()
                + ",knowledge=" + plan.isNeedsKnowledge() + "]";
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
                      "needsKnowledge":false,
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

                    3. Evaluate the whole request, including purpose and context. A
                       live lookup can legitimately coexist with knowledge when the
                       user asks what the live information means for their travel.
                       Example: weather plus an explicit request to prepare for a visit
                       can produce needsWeather=true and needsKnowledge=true. A plain
                       weather lookup can remain needsKnowledge=false.

                    4. Never copy capability flags from the existing trip.

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
                      "needsKnowledge":false,
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
                      "needsKnowledge":false,
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
                      "needsKnowledge":false,
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
                      "needsKnowledge":false,
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
                      "needsKnowledge":false,
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
                      "needsKnowledge":false,
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
                      "needsKnowledge":false,
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
    private Map<String, Object> toUpdates(TravelState state, IntentPlan plan) {
        Map<String, Object> updates = new LinkedHashMap<>();
        if (plan == null) {
            plan = emptyPlan();
        }

        // Intent is a CURRENT-TURN decision. Never OR it with checkpoint history.
        // Historical trip data remains in the state/result collections, while these
        // fields describe only what the latest semantic decision wants to execute.
        // This is the key isolation boundary that prevents an old trip from leaking
        // flights/hotels/budget into a new specialist request.
        updates.put(TravelState.REQUEST_TYPE, plan.getRequestType());
        updates.put(TravelState.NEEDS_FLIGHTS, plan.isNeedsFlights());
        updates.put(TravelState.NEEDS_HOTELS, plan.isNeedsHotels());
        updates.put(TravelState.NEEDS_RESEARCH, plan.isNeedsResearch());
        updates.put(TravelState.NEEDS_WEATHER, plan.isNeedsWeather());
        updates.put(TravelState.NEEDS_BUDGET, plan.isNeedsBudget());
        updates.put(TravelState.NEEDS_ITINERARY, plan.isNeedsItinerary());
        updates.put(TravelState.NEEDS_KNOWLEDGE, plan.isNeedsKnowledge());

        // RUN_* is also exactly the semantic capability vector for this turn.
        updates.put(TravelState.RUN_FLIGHTS, plan.isNeedsFlights());
        updates.put(TravelState.RUN_HOTELS, plan.isNeedsHotels());
        updates.put(TravelState.RUN_RESEARCH, plan.isNeedsResearch());
        updates.put(TravelState.RUN_WEATHER, plan.isNeedsWeather());
        updates.put(TravelState.RUN_BUDGET, plan.isNeedsBudget());
        updates.put(TravelState.RUN_ITINERARY, plan.isNeedsItinerary());

        updates.put(TravelState.PLAN_STRATEGY, plan.getStrategy());
        updates.put(TravelState.PLAN_PRIORITY, plan.getPriority());
        updates.put(TravelState.INTENT_CONFIDENCE, plan.getConfidence());

        // Clear turn-scoped generated artifacts before executing this semantic
        // request. The checkpoint intentionally retains historical domain data,
        // but transient RAG/tips/validation output must never leak into a new turn.
        updates.put(TravelState.RAG_DECISION, "skip");
        updates.put(TravelState.RAG_QUERY, "");
        updates.put(TravelState.RAG_CONTEXT, "");
        updates.put(TravelState.RAG_ANSWER, "");
        updates.put(TravelState.RAG_SOURCES, java.util.List.of());
        updates.put(TravelState.RAG_ITERATIONS, 0);
        updates.put(TravelState.RAG_SUFFICIENT, Boolean.FALSE);
        updates.put(TravelState.RAG_RETRIEVAL_METHOD, "none");
        updates.put(TravelState.RAG_CANDIDATE_COUNT, 0);
        updates.put(TravelState.RAG_RERANKED_COUNT, 0);
        updates.put(TravelState.RAG_CONTEXT_CHARS, 0);
        updates.put(TravelState.RAG_EVIDENCE_SCORE, 0.0d);
        updates.put(TravelState.RAG_GROUNDEDNESS, 0.0d);
        updates.put(TravelState.RAG_JUDGE_PASS, Boolean.TRUE);
        updates.put(TravelState.RAG_JUDGE_REASON, "not_applicable");
        updates.put(TravelState.RAG_DESTINATION, "");
        updates.put(TravelState.RAG_COUNTRY, "");
        updates.put(TravelState.RAG_TOPICS, java.util.List.of());
        updates.put(TravelState.FINAL_TIPS, "");
        updates.put(TravelState.VALIDATION_ERRORS, java.util.List.of());
        updates.put(TravelState.SEMANTIC_NOTES, java.util.List.of());

        if (state != null) {
            updates.put(TravelState.TRIP_REQUIREMENTS,
                    TripRequirementsParser.parse(state.userRequest()));
            updates.put(TravelState.LAST_DECISION,
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
        @SuppressWarnings("unused")
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
                              "needsKnowledge":false,
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
                            - needsKnowledge=true for durable travel knowledge or practical travel guidance
                              requested alongside another capability. It may be true for a live
                              lookup when the user asks what that information means for the trip;
                              keep it false for a purely factual live lookup.
                            - Judge this from the complete latest request and its purpose. Do not copy
                              capabilities from the existing checkpoint.

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
            llm.setNeedsKnowledge(true);
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
        plan.setNeedsKnowledge(false);

        plan.setStrategy("none");
        plan.setPriority("none");
        plan.setConfidence(0.0);

        return plan;
    }
}