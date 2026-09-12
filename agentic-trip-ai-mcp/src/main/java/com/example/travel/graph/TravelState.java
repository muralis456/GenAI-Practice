package com.example.travel.graph;

import com.example.travel.dto.TravelRequest;
import com.example.travel.model.AgentDecision;
import com.example.travel.model.AgentStep;
import com.example.travel.model.BudgetSummary;
import com.example.travel.model.FlightOption;
import com.example.travel.model.HotelOption;
import com.example.travel.model.Itinerary;
import com.example.travel.model.ModificationRequest;
import com.example.travel.model.NodeFailureInfo;
import com.example.travel.model.PlanQualityScore;
import com.example.travel.model.ProvenanceEvent;
import com.example.travel.model.ReplanStrategy;
import com.example.travel.model.SemanticValidationResult;
import com.example.travel.model.SupervisorAssessment;
import com.example.travel.model.TravelAttraction;
import com.example.travel.model.TravelResearch;
import com.example.travel.model.TripRequirements;
import com.example.travel.model.WeatherForecast;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.example.travel.graph.TravelStateKeys.Rag;

@Slf4j
public class TravelState extends AgentState {

    /** Sentinel for "no numeric budget ceiling" — LangGraph schema defaults cannot be null. */
    public static final BigDecimal UNSET_BUDGET = new BigDecimal("-1");

    // Re-export grouped keys for backward compatibility (see TravelStateKeys).
    public static final String USER_REQUEST = TravelStateKeys.Request.USER_REQUEST;
    public static final String USER_ID = TravelStateKeys.Request.USER_ID;
    public static final String SELECTED_MODEL = TravelStateKeys.Request.SELECTED_MODEL;
    public static final String HISTORY_CONTEXT = TravelStateKeys.Request.HISTORY_CONTEXT;
    public static final String ORIGIN = TravelStateKeys.Trip.ORIGIN;
    public static final String DESTINATION = TravelStateKeys.Trip.DESTINATION;
    public static final String DEPARTURE_DATE = TravelStateKeys.Trip.DEPARTURE_DATE;
    public static final String RETURN_DATE = TravelStateKeys.Trip.RETURN_DATE;
    public static final String TRAVELERS = TravelStateKeys.Trip.TRAVELERS;
    public static final String BUDGET = TravelStateKeys.Budget.BUDGET;
    public static final String BUDGET_LABEL = TravelStateKeys.Budget.BUDGET_LABEL;
    public static final String TRAVEL_STYLE = TravelStateKeys.Preferences.TRAVEL_STYLE;
    public static final String ORIGIN_IATA = TravelStateKeys.Trip.ORIGIN_IATA;
    public static final String DESTINATION_IATA = TravelStateKeys.Trip.DESTINATION_IATA;
    public static final String FLIGHTS = TravelStateKeys.Results.FLIGHTS;
    public static final String HOTELS = TravelStateKeys.Results.HOTELS;
    public static final String ATTRACTIONS = TravelStateKeys.Results.ATTRACTIONS;
    public static final String RESEARCH = TravelStateKeys.Results.RESEARCH;
    public static final String ITINERARY = TravelStateKeys.Results.ITINERARY;
    public static final String BUDGET_SUMMARY = TravelStateKeys.Budget.BUDGET_SUMMARY;
    public static final String VALIDATION_ERRORS = TravelStateKeys.Validation.VALIDATION_ERRORS;
    public static final String RETRY_COUNT = TravelStateKeys.Control.RETRY_COUNT;
    public static final String MAX_RETRIES = TravelStateKeys.Control.MAX_RETRIES;
    public static final String FINAL_TIPS = TravelStateKeys.Results.FINAL_TIPS;
    /** @deprecated Use {@link #FINAL_TIPS}. */
    @Deprecated
    public static final String FINAL_PLAN = TravelStateKeys.Results.FINAL_PLAN;
    public static final String REPLAN_NOTES = TravelStateKeys.Planning.REPLAN_NOTES;
    public static final String COST_FACTOR = TravelStateKeys.Budget.COST_FACTOR;
    public static final String WEATHER = TravelStateKeys.Results.WEATHER;
    public static final String PIPELINE = TravelStateKeys.Control.PIPELINE;
    public static final String AWAITING_APPROVAL = TravelStateKeys.Hitl.AWAITING_APPROVAL;
    public static final String HITL_DECISION = TravelStateKeys.Hitl.HITL_DECISION;
    public static final String PREFERRED_AIRPORT = TravelStateKeys.Trip.PREFERRED_AIRPORT;
    public static final String CURRENCY = TravelStateKeys.Trip.CURRENCY;
    public static final String REQUEST_TYPE = TravelStateKeys.Request.REQUEST_TYPE;
    public static final String NEEDS_FLIGHTS = TravelStateKeys.Needs.NEEDS_FLIGHTS;
    public static final String NEEDS_HOTELS = TravelStateKeys.Needs.NEEDS_HOTELS;
    public static final String NEEDS_RESEARCH = TravelStateKeys.Needs.NEEDS_RESEARCH;
    public static final String NEEDS_WEATHER = TravelStateKeys.Needs.NEEDS_WEATHER;
    public static final String NEEDS_BUDGET = TravelStateKeys.Needs.NEEDS_BUDGET;
    public static final String NEEDS_ITINERARY = TravelStateKeys.Needs.NEEDS_ITINERARY;
    public static final String NEEDS_KNOWLEDGE = TravelStateKeys.Needs.NEEDS_KNOWLEDGE;
    public static final String RUN_FLIGHTS = TravelStateKeys.Run.RUN_FLIGHTS;
    public static final String RUN_HOTELS = TravelStateKeys.Run.RUN_HOTELS;
    public static final String RUN_RESEARCH = TravelStateKeys.Run.RUN_RESEARCH;
    public static final String RUN_WEATHER = TravelStateKeys.Run.RUN_WEATHER;
    public static final String RUN_BUDGET = TravelStateKeys.Run.RUN_BUDGET;
    public static final String RUN_ITINERARY = TravelStateKeys.Run.RUN_ITINERARY;
    public static final String PLAN_STRATEGY = TravelStateKeys.Planning.PLAN_STRATEGY;
    public static final String PLAN_PRIORITY = TravelStateKeys.Planning.PLAN_PRIORITY;
    public static final String LAST_DECISION = TravelStateKeys.Control.LAST_DECISION;
    public static final String REPLAN_STRATEGY = TravelStateKeys.Planning.REPLAN_STRATEGY;
    public static final String SEMANTIC_NOTES = TravelStateKeys.Validation.SEMANTIC_NOTES;
    public static final String PROVENANCE = TravelStateKeys.Control.PROVENANCE;
    public static final String HOTEL_CHEAPER = TravelStateKeys.Preferences.HOTEL_CHEAPER;
    public static final String FLIGHT_PREFERENCE = TravelStateKeys.Preferences.FLIGHT_PREFERENCE;
    public static final String TRIP_REQUIREMENTS = TravelStateKeys.Preferences.TRIP_REQUIREMENTS;
    public static final String MODEL_POLICY = TravelStateKeys.Request.MODEL_POLICY;
    public static final String INTENT_CONFIDENCE = TravelStateKeys.Request.INTENT_CONFIDENCE;
    public static final String MODIFICATION = TravelStateKeys.Hitl.MODIFICATION;
    public static final String DISPATCH_ROUTE = TravelStateKeys.Control.DISPATCH_ROUTE;
    public static final String SUPERVISOR_DECISION = TravelStateKeys.Control.SUPERVISOR_DECISION;
    public static final String GRAPH_THREAD_ID = TravelStateKeys.Request.GRAPH_THREAD_ID;
    public static final String PLAN_QUALITY = TravelStateKeys.Validation.PLAN_QUALITY;
    public static final String SEMANTIC_VALIDATION = TravelStateKeys.Validation.SEMANTIC_VALIDATION;
    public static final String SUPERVISOR_ASSESSMENT = TravelStateKeys.Control.SUPERVISOR_ASSESSMENT;
    public static final String NODE_FAILURE = TravelStateKeys.Control.NODE_FAILURE;
    public static final String RAG_ENABLED = Rag.RAG_ENABLED;
    public static final String RAG_DECISION = Rag.RAG_DECISION;
    public static final String RAG_QUERY = Rag.RAG_QUERY;
    public static final String RAG_CONTEXT = Rag.RAG_CONTEXT;
    public static final String RAG_SOURCES = Rag.RAG_SOURCES;
    public static final String RAG_ITERATIONS = Rag.RAG_ITERATIONS;
    public static final String RAG_SUFFICIENT = Rag.RAG_SUFFICIENT;
    public static final String RAG_RETRIEVAL_METHOD = Rag.RAG_RETRIEVAL_METHOD;
    public static final String RAG_CANDIDATE_COUNT = Rag.RAG_CANDIDATE_COUNT;
    public static final String RAG_RERANKED_COUNT = Rag.RAG_RERANKED_COUNT;
    public static final String RAG_CONTEXT_CHARS = Rag.RAG_CONTEXT_CHARS;
    public static final String RAG_EVIDENCE_SCORE = Rag.RAG_EVIDENCE_SCORE;
    public static final String RAG_GROUNDEDNESS = Rag.RAG_GROUNDEDNESS;
    public static final String RAG_JUDGE_PASS = Rag.RAG_JUDGE_PASS;
    public static final String RAG_JUDGE_REASON = Rag.RAG_JUDGE_REASON;
    public static final String RAG_DESTINATION = Rag.RAG_DESTINATION;
    public static final String RAG_COUNTRY = Rag.RAG_COUNTRY;
    public static final String RAG_TOPICS = Rag.RAG_TOPICS;

    public static final Map<String, Channel<?>> SCHEMA = TravelStateSchema.SCHEMA;

    public TravelState(Map<String, Object> initData) {
        super(initData);
    }

    public static Map<String, Object> fromRequest(TravelRequest request, String historyContext) {
        LocalDate today = LocalDate.now();
        String prompt = firstNonBlank(request.getPrompt(), request.getPreferences(),
                "Plan a balanced family-friendly trip with good food and local experiences.");
        int travelers = defaultInt(request.getAdults(), 1) + defaultInt(request.getChildren(), 0);

        Map<String, Object> input = new LinkedHashMap<>();
        input.put(USER_REQUEST, prompt);
        input.put(USER_ID, firstNonBlank(request.getUserId(), "anonymous"));
        input.put(SELECTED_MODEL, blankToEmpty(request.getSelectedModel()));
        input.put(HISTORY_CONTEXT, historyContext == null ? "" : historyContext);
        input.put(ORIGIN, blankToEmpty(request.getDepartureCity()));
        input.put(DESTINATION, blankToEmpty(request.getDestination()));
        input.put(DEPARTURE_DATE, parseDate(request.getDepartureDate(), today));
        input.put(RETURN_DATE, parseDate(request.getReturnDate(), today.plusDays(5)));
        input.put(TRAVELERS, Math.max(travelers, 1));
        BigDecimal budget = parseBudget(request.getBudget());
        input.put(BUDGET, budget == null ? UNSET_BUDGET : budget);
        input.put(BUDGET_LABEL, firstNonBlank(request.getBudget(), "medium"));
        input.put(TRAVEL_STYLE, firstNonBlank(request.getTravelStyle(), "balanced"));
        input.put(RETRY_COUNT, 0);
        input.put(MAX_RETRIES, 2);
        input.put(COST_FACTOR, BigDecimal.ONE);
        input.put(HOTEL_CHEAPER, Boolean.FALSE);
        input.put(FLIGHT_PREFERENCE, "balanced");

        // Every routing flag is explicitly initialized. Missing flags must never
        // default to true because that can accidentally execute old specialists.
        input.put(NEEDS_FLIGHTS, false);
        input.put(NEEDS_HOTELS, false);
        input.put(NEEDS_RESEARCH, false);
        input.put(NEEDS_WEATHER, false);
        input.put(NEEDS_BUDGET, false);
        input.put(NEEDS_ITINERARY, false);
        input.put(NEEDS_KNOWLEDGE, false);
        input.put(RUN_FLIGHTS, false);
        input.put(RUN_HOTELS, false);
        input.put(RUN_RESEARCH, false);
        input.put(RUN_WEATHER, false);
        input.put(RUN_BUDGET, false);
        input.put(RUN_ITINERARY, false);
        input.put(MODEL_POLICY, com.example.travel.service.ModelRoutingContext.normalize(request.getSelectedModel()));
        input.put(VALIDATION_ERRORS, new ArrayList<String>());
        input.put(PIPELINE, new ArrayList<AgentStep>());
        input.put(AWAITING_APPROVAL, Boolean.TRUE);
        input.put(HITL_DECISION, "");
        input.put(CURRENCY, "INR");
        input.put(ITINERARY, new Itinerary());
        input.put(BUDGET_SUMMARY, new BudgetSummary());
        input.put(WEATHER, new WeatherForecast("", "", false));
        input.put(RAG_ENABLED, Boolean.TRUE);
        input.put(RAG_DECISION, "skip");
        input.put(RAG_QUERY, "");
        input.put(RAG_CONTEXT, "");
        input.put(RAG_SOURCES, new ArrayList<String>());
        input.put(RAG_ITERATIONS, 0);
        input.put(RAG_SUFFICIENT, Boolean.FALSE);
        input.put(RAG_RETRIEVAL_METHOD, "none");
        input.put(RAG_CANDIDATE_COUNT, 0);
        input.put(RAG_RERANKED_COUNT, 0);
        input.put(RAG_CONTEXT_CHARS, 0);
        input.put(RAG_EVIDENCE_SCORE, 0.0d);
        input.put(RAG_GROUNDEDNESS, 0.0d);
        input.put(RAG_JUDGE_PASS, Boolean.TRUE);
        input.put(RAG_JUDGE_REASON, "not_applicable");
        input.put(RAG_DESTINATION, "");
        input.put(RAG_COUNTRY, "");
        input.put(RAG_TOPICS, new ArrayList<String>());
        return input;
    }

    public boolean ragEnabled() {
        return Boolean.TRUE.equals(this.<Boolean>value(RAG_ENABLED).orElse(Boolean.FALSE));
    }

    public String ragDecision() {
        return this.<String>value(RAG_DECISION).orElse("skip");
    }

    public String ragQuery() {
        return this.<String>value(RAG_QUERY).orElse("");
    }

    public String ragContext() {
        return this.<String>value(RAG_CONTEXT).orElse("");
    }

    public List<String> ragSources() {
        return this.<List<String>>value(RAG_SOURCES).orElseGet(List::of);
    }

    public int ragIterations() {
        Object value = this.value(RAG_ITERATIONS).orElse(0);
        return value instanceof Number number ? number.intValue() : 0;
    }

    public boolean ragSufficient() {
        return Boolean.TRUE.equals(this.<Boolean>value(RAG_SUFFICIENT).orElse(Boolean.FALSE));
    }

    public String ragRetrievalMethod() {
        return this.<String>value(RAG_RETRIEVAL_METHOD).orElse("none");
    }

    public int ragCandidateCount() {
        Object value = this.value(RAG_CANDIDATE_COUNT).orElse(0);
        return value instanceof Number number ? number.intValue() : 0;
    }

    public int ragRerankedCount() {
        Object value = this.value(RAG_RERANKED_COUNT).orElse(0);
        return value instanceof Number number ? number.intValue() : 0;
    }

    public double ragGroundedness() {
        Object value = this.value(RAG_GROUNDEDNESS).orElse(0.0d);
        return value instanceof Number number ? number.doubleValue() : 0.0d;
    }

    public boolean ragJudgePass() {
        return Boolean.TRUE.equals(this.<Boolean>value(RAG_JUDGE_PASS).orElse(Boolean.TRUE));
    }

    public String ragJudgeReason() {
        return this.<String>value(RAG_JUDGE_REASON).orElse("not_applicable");
    }

    public double ragEvidenceScore() {
        Object value = this.value(RAG_EVIDENCE_SCORE).orElse(0.0d);
        return value instanceof Number number ? number.doubleValue() : 0.0d;
    }

    public int ragContextChars() {
        Object value = this.value(RAG_CONTEXT_CHARS).orElse(0);
        return value instanceof Number number ? number.intValue() : 0;
    }

    public String ragDestination() { return this.<String>value(RAG_DESTINATION).orElse(""); }

    public String ragCountry() { return this.<String>value(RAG_COUNTRY).orElse(""); }

    public List<String> ragTopics() { return this.<List<String>>value(RAG_TOPICS).orElseGet(List::of); }

    public String userRequest() {
        return this.<String>value(USER_REQUEST).orElse("");
    }

    public String userId() {
        return this.<String>value(USER_ID).orElse("anonymous");
    }

    public String selectedModel() {
        return this.<String>value(SELECTED_MODEL).orElse("");
    }

    public String historyContext() {
        return this.<String>value(HISTORY_CONTEXT).orElse("");
    }

    public String origin() {
        return this.<String>value(ORIGIN).orElse("");
    }

    public String destination() {
        return this.<String>value(DESTINATION).orElse("");
    }

    public LocalDate departureDate() {
        return this.<LocalDate>value(DEPARTURE_DATE).orElse(LocalDate.now());
    }

    public LocalDate returnDate() {
        return this.<LocalDate>value(RETURN_DATE).orElse(LocalDate.now().plusDays(5));
    }

    public Integer travelers() {
        return this.<Integer>value(TRAVELERS).orElse(1);
    }

    public BigDecimal budget() {
        BigDecimal value = this.<BigDecimal>value(BUDGET).orElse(UNSET_BUDGET);
        return UNSET_BUDGET.compareTo(value) == 0 ? null : value;
    }

    public String budgetLabel() {
        return this.<String>value(BUDGET_LABEL).orElse("medium");
    }

    public String travelStyle() {
        return this.<String>value(TRAVEL_STYLE).orElse("balanced");
    }

    public String originIata() {
        return this.<String>value(ORIGIN_IATA).orElse("");
    }

    public String destinationIata() {
        return this.<String>value(DESTINATION_IATA).orElse("");
    }

    public List<FlightOption> flights() {
        return this.<List<FlightOption>>value(FLIGHTS).orElseGet(List::of);
    }

    public List<HotelOption> hotels() {
        return this.<List<HotelOption>>value(HOTELS).orElseGet(List::of);
    }

    public List<TravelAttraction> attractions() {
        return this.<List<TravelAttraction>>value(ATTRACTIONS).orElseGet(List::of);
    }

    public List<TravelResearch> research() {
        return this.<List<TravelResearch>>value(RESEARCH).orElseGet(List::of);
    }

    public Itinerary itinerary() {
        Itinerary value = this.<Itinerary>value(ITINERARY).orElseGet(Itinerary::new);
        return value.isEmpty() ? null : value;
    }

    public BudgetSummary budgetSummary() {
        BudgetSummary value = this.<BudgetSummary>value(BUDGET_SUMMARY).orElseGet(BudgetSummary::new);
        return value.getEstimatedCost() == null ? null : value;
    }

    public List<String> validationErrors() {
        return this.<List<String>>value(VALIDATION_ERRORS).orElseGet(List::of);
    }

    public int retryCount() {
        Object value = this.value(RETRY_COUNT).orElse(0);
        if (value instanceof Number number) {
            return number.intValue();
        }
        return 0;
    }

    public String finalTips() {
        String tips = this.<String>value(FINAL_TIPS).orElse("");
        if (!isBlank(tips)) {
            return tips;
        }
        return this.<String>value(FINAL_PLAN).orElse("");
    }

    /** @deprecated Use {@link #finalTips()}. */
    @Deprecated
    public String finalPlan() {
        return finalTips();
    }

    public int maxRetries() {
        Object value = this.value(MAX_RETRIES).orElse(2);
        if (value instanceof Number number) {
            return number.intValue();
        }
        return 2;
    }

    public String replanNotes() {
        return this.<String>value(REPLAN_NOTES).orElse("");
    }

    public String replanGuidance() {
        return replanNotes();
    }

    public BigDecimal costFactor() {
        Object value = this.value(COST_FACTOR).orElse(BigDecimal.ONE);
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        return BigDecimal.ONE;
    }

    public WeatherForecast weather() {
        WeatherForecast value = this.<WeatherForecast>value(WEATHER).orElseGet(() -> new WeatherForecast("", "", false));
        return (value.getLocation() == null || value.getLocation().isBlank()) && (value.getSummary() == null || value.getSummary().isBlank())
                ? null
                : value;
    }

    public List<AgentStep> pipeline() {
        return this.<List<AgentStep>>value(PIPELINE).orElseGet(List::of);
    }

    public boolean awaitingApproval() {
        return Boolean.TRUE.equals(this.<Boolean>value(AWAITING_APPROVAL).orElse(Boolean.TRUE));
    }

    public String hitlDecision() {
        return this.<String>value(HITL_DECISION).orElse("");
    }

    public String preferredAirport() {
        return this.<String>value(PREFERRED_AIRPORT).orElse("");
    }

    public String currency() {
        return this.<String>value(CURRENCY).orElse("INR");
    }

    public String requestType() {
        return this.<String>value(REQUEST_TYPE).orElse("TRIP_PLANNING");
    }

    public boolean needsFlights() {
        return flag(NEEDS_FLIGHTS);
    }

    public boolean needsHotels() {
        return flag(NEEDS_HOTELS);
    }

    public boolean needsResearch() {
        return flag(NEEDS_RESEARCH);
    }

    public boolean needsWeather() {
        return flag(NEEDS_WEATHER);
    }

    public boolean needsBudget() {
        return flag(NEEDS_BUDGET);
    }

    public boolean needsItinerary() {
        return flag(NEEDS_ITINERARY);
    }

    public boolean needsKnowledge() {
        return flag(NEEDS_KNOWLEDGE);
    }

    public boolean runFlights() {
        return flag(RUN_FLIGHTS);
    }

    public boolean runHotels() {
        return flag(RUN_HOTELS);
    }

    public boolean runResearch() {
        return flag(RUN_RESEARCH);
    }

    public boolean runWeather() {
        return flag(RUN_WEATHER);
    }

    public boolean runBudget() {
        return flag(RUN_BUDGET);
    }

    public boolean runItinerary() {
        return flag(RUN_ITINERARY);
    }

    public static void applyIntentAndRun(Map<String, Object> updates, com.example.travel.model.IntentPlan plan) {
        updates.put(NEEDS_FLIGHTS, plan.isNeedsFlights());
        updates.put(NEEDS_HOTELS, plan.isNeedsHotels());
        updates.put(NEEDS_RESEARCH, plan.isNeedsResearch());
        updates.put(NEEDS_WEATHER, plan.isNeedsWeather());
        updates.put(NEEDS_BUDGET, plan.isNeedsBudget());
        updates.put(NEEDS_ITINERARY, plan.isNeedsItinerary());
        updates.put(NEEDS_KNOWLEDGE, plan.isNeedsKnowledge());
        updates.put(RUN_FLIGHTS, plan.isNeedsFlights());
        updates.put(RUN_HOTELS, plan.isNeedsHotels());
        updates.put(RUN_RESEARCH, plan.isNeedsResearch());
        updates.put(RUN_WEATHER, plan.isNeedsWeather());
        updates.put(RUN_BUDGET, plan.isNeedsBudget());
        updates.put(RUN_ITINERARY, plan.isNeedsItinerary());
    }

    /** Whether flight results exist in state (independent of selective replan routing flags). */
    public boolean hasUsableFlights() {
        return flights().stream().anyMatch(flight ->
                flight != null && !"unavailable".equalsIgnoreCase(flight.getStatus()));
    }

    public boolean hasHotelResults() {
        return !hotels().isEmpty();
    }

    public boolean hasBudgetEstimate() {
        BudgetSummary budget = budgetSummary();
        return budget != null && budget.getEstimatedCost() != null && budget.getEstimatedCost().signum() > 0;
    }

    public boolean includeFlightsInReport() {
        return needsFlights() || hasUsableFlights();
    }

    public boolean includeHotelsInReport() {
        return needsHotels() || hasHotelResults();
    }

    public boolean includeBudgetInReport() {
        return needsBudget() || hasBudgetEstimate();
    }

    public boolean includeItineraryInReport() {
        Itinerary plan = itinerary();
        return needsItinerary() || (plan != null && !plan.isEmpty());
    }

    public boolean includeWeatherInReport() {
        WeatherForecast forecast = weather();
        return needsWeather() || (forecast != null && !TravelState.isBlank(forecast.getSummary()));
    }

    public String planStrategy() {
        return this.<String>value(PLAN_STRATEGY).orElse("parallel_search");
    }

    public String planPriority() {
        return this.<String>value(PLAN_PRIORITY).orElse("balanced");
    }

    public AgentDecision lastDecision() {
        return this.<AgentDecision>value(LAST_DECISION).orElseGet(AgentDecision::new);
    }

    public ReplanStrategy replanStrategy() {
        return this.<ReplanStrategy>value(REPLAN_STRATEGY).orElseGet(ReplanStrategy::new);
    }

    public List<String> semanticNotes() {
        return this.<List<String>>value(SEMANTIC_NOTES).orElseGet(List::of);
    }

    public List<ProvenanceEvent> provenance() {
        return this.<List<ProvenanceEvent>>value(PROVENANCE).orElseGet(List::of);
    }

    private boolean flag(String key) {
        return Boolean.TRUE.equals(this.<Boolean>value(key).orElse(Boolean.FALSE));
    }

    public long nights() {
        long days = ChronoUnit.DAYS.between(departureDate(), returnDate());
        return Math.max(1, days);
    }

    public boolean overBudget() {
        return budgetSummary() != null && !budgetSummary().isWithinBudget();
    }

    public boolean hotelCheaper() {
        return Boolean.TRUE.equals(this.<Boolean>value(HOTEL_CHEAPER).orElse(Boolean.FALSE));
    }

    public String flightPreference() {
        return this.<String>value(FLIGHT_PREFERENCE).orElse("balanced");
    }

    public String modelPolicy() {
        return this.<String>value(MODEL_POLICY).orElse("BALANCED");
    }

    public double intentConfidence() {
        Object value = this.value(INTENT_CONFIDENCE).orElse(1.0d);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return 1.0d;
    }

    public ModificationRequest modification() {
        return this.<ModificationRequest>value(MODIFICATION).orElseGet(ModificationRequest::new);
    }

    public String supervisorDecision() {
        return this.<String>value(SUPERVISOR_DECISION).orElse("");
    }

    public String dispatchRoute() {
        return this.<String>value(DISPATCH_ROUTE).orElse("");
    }

    public String graphThreadId() {
        return this.<String>value(GRAPH_THREAD_ID).orElse("");
    }

    public PlanQualityScore planQuality() {
        return this.<PlanQualityScore>value(PLAN_QUALITY).orElseGet(PlanQualityScore::new);
    }

    public SemanticValidationResult semanticValidation() {
        return this.<SemanticValidationResult>value(SEMANTIC_VALIDATION).orElseGet(SemanticValidationResult::new);
    }

    public SupervisorAssessment supervisorAssessment() {
        return this.<SupervisorAssessment>value(SUPERVISOR_ASSESSMENT).orElseGet(SupervisorAssessment::new);
    }

    public TripRequirements tripRequirements() {
        return this.<TripRequirements>value(TRIP_REQUIREMENTS).orElseGet(TripRequirements::new);
    }

    public NodeFailureInfo nodeFailure() {
        return this.<NodeFailureInfo>value(NODE_FAILURE).orElseGet(NodeFailureInfo::new);
    }

    public boolean shouldReplan() {
        boolean deterministicFailure = !validationErrors().isEmpty();
        boolean semanticFailure = !semanticNotes().isEmpty() || semanticValidation().failed();
        boolean qualityFailure = planQuality() != null && planQuality().getOverall() > 0
                && planQuality().getOverall() < PlanQualityScore.PASS_THRESHOLD;
        boolean ragGroundingFailure = ragEnabled() && ragSufficient() && !ragJudgePass();
        return (deterministicFailure || semanticFailure || qualityFailure || ragGroundingFailure) && retryCount() < maxRetries();
    }

    public boolean shouldReplanForBudget() {
        return overBudget() && retryCount() < maxRetries();
    }

    public static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (!isBlank(value)) {
                return value.trim();
            }
        }
        return "";
    }

    public static String blankToEmpty(String value) {
        return isBlank(value) ? "" : value.trim();
    }

    public static LocalDate parseDate(String value, LocalDate fallback) {
        if (isBlank(value)) {
            return fallback;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    public static BigDecimal parseBudget(String value) {
        if (isBlank(value)) {
            return null;
        }
        String normalized = value.toLowerCase().replace(",", "").replace("₹", "").replace("rs", "").trim();
        if (normalized.matches(".*\\d+(\\.\\d+)?\\s*lakh.*") || normalized.matches(".*\\d+(\\.\\d+)?\\s*lac.*")) {
            String number = normalized.replaceAll("[^0-9.]", "");
            return parseDecimal(number).multiply(BigDecimal.valueOf(100_000));
        }
        if (normalized.matches("\\d+(\\.\\d+)?\\s*l\\b") || normalized.endsWith("l")) {
            String number = normalized.replaceAll("[^0-9.]", "");
            return parseDecimal(number).multiply(BigDecimal.valueOf(100_000));
        }
        String digits = normalized.replaceAll("[^0-9.]", "");
        if (digits.isBlank()) {
            return null;
        }
        return parseDecimal(digits);
    }

    private static BigDecimal parseDecimal(String digits) {
        try {
            return new BigDecimal(digits);
        } catch (NumberFormatException ignored) {
            return BigDecimal.ZERO;
        }
    }

    public static Map<String, Object> trace(String node, String status, String detail) {
        return Map.of(PIPELINE, List.of(new AgentStep(node, AgentStep.normalizeStatus(status), detail)));
    }

    public static Map<String, Object> provenance(ProvenanceEvent event) {
        return Map.of(PROVENANCE, List.of(event));
    }

    private static int defaultInt(Integer value, int fallback) {
        return value == null ? fallback : value;
    }
}
