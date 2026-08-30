package com.example.travel.graph;

import com.example.travel.dto.TravelRequest;
import com.example.travel.model.AgentStep;
import com.example.travel.model.BudgetSummary;
import com.example.travel.model.FlightOption;
import com.example.travel.model.HotelOption;
import com.example.travel.model.Itinerary;
import com.example.travel.model.TravelAttraction;
import com.example.travel.model.TravelResearch;
import com.example.travel.model.WeatherForecast;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TravelState extends AgentState {

    /** Sentinel for "no numeric budget ceiling" — LangGraph schema defaults cannot be null. */
    public static final BigDecimal UNSET_BUDGET = new BigDecimal("-1");

    public static final String USER_REQUEST = "userRequest";
    public static final String USER_ID = "userId";
    public static final String SELECTED_MODEL = "selectedModel";
    public static final String HISTORY_CONTEXT = "historyContext";
    public static final String ORIGIN = "origin";
    public static final String DESTINATION = "destination";
    public static final String DEPARTURE_DATE = "departureDate";
    public static final String RETURN_DATE = "returnDate";
    public static final String TRAVELERS = "travelers";
    public static final String BUDGET = "budget";
    public static final String BUDGET_LABEL = "budgetLabel";
    public static final String TRAVEL_STYLE = "travelStyle";
    public static final String ORIGIN_IATA = "originIata";
    public static final String DESTINATION_IATA = "destinationIata";
    public static final String FLIGHTS = "flights";
    public static final String HOTELS = "hotels";
    public static final String ATTRACTIONS = "attractions";
    public static final String RESEARCH = "research";
    public static final String ITINERARY = "itinerary";
    public static final String BUDGET_SUMMARY = "budgetSummary";
    public static final String VALIDATION_ERRORS = "validationErrors";
    public static final String RETRY_COUNT = "retryCount";
    public static final String MAX_RETRIES = "maxRetries";
    public static final String FINAL_PLAN = "finalPlan";
    public static final String REPLAN_NOTES = "replanNotes";
    public static final String COST_FACTOR = "costFactor";
    public static final String WEATHER = "weather";
    public static final String PIPELINE = "pipeline";
    public static final String AWAITING_APPROVAL = "awaitingApproval";
    public static final String PREFERRED_AIRPORT = "preferredAirport";
    public static final String CURRENCY = "currency";

    public static final Map<String, Channel<?>> SCHEMA;

    static {
        Map<String, Channel<?>> schema = new LinkedHashMap<>();
        schema.put(USER_REQUEST, Channels.base(() -> ""));
        schema.put(USER_ID, Channels.base(() -> "anonymous"));
        schema.put(SELECTED_MODEL, Channels.base(() -> "llama3.2:3b"));
        schema.put(HISTORY_CONTEXT, Channels.base(() -> ""));
        schema.put(ORIGIN, Channels.base(() -> ""));
        schema.put(DESTINATION, Channels.base(() -> ""));
        schema.put(DEPARTURE_DATE, Channels.base(() -> LocalDate.now()));
        schema.put(RETURN_DATE, Channels.base(() -> LocalDate.now().plusDays(5)));
        schema.put(TRAVELERS, Channels.base(() -> 1));
        schema.put(BUDGET, Channels.base(() -> UNSET_BUDGET));
        schema.put(BUDGET_LABEL, Channels.base(() -> "medium"));
        schema.put(TRAVEL_STYLE, Channels.base(() -> "balanced"));
        schema.put(ORIGIN_IATA, Channels.base(() -> ""));
        schema.put(DESTINATION_IATA, Channels.base(() -> ""));
        schema.put(FLIGHTS, Channels.base(() -> new ArrayList<FlightOption>()));
        schema.put(HOTELS, Channels.base(() -> new ArrayList<HotelOption>()));
        schema.put(ATTRACTIONS, Channels.base(() -> new ArrayList<TravelAttraction>()));
        schema.put(RESEARCH, Channels.base(() -> new ArrayList<TravelResearch>()));
        schema.put(ITINERARY, Channels.base(() -> new Itinerary()));
        schema.put(BUDGET_SUMMARY, Channels.base(() -> new BudgetSummary()));
        schema.put(VALIDATION_ERRORS, Channels.base(() -> new ArrayList<String>()));
        schema.put(RETRY_COUNT, Channels.base(() -> 0));
        schema.put(MAX_RETRIES, Channels.base(() -> 2));
        schema.put(FINAL_PLAN, Channels.base(() -> ""));
        schema.put(REPLAN_NOTES, Channels.base(() -> ""));
        schema.put(COST_FACTOR, Channels.base(() -> BigDecimal.ONE));
        schema.put(WEATHER, Channels.base(() -> new WeatherForecast("", "", false)));
        schema.put(PIPELINE, Channels.appender(() -> new ArrayList<AgentStep>()));
        schema.put(AWAITING_APPROVAL, Channels.base(() -> Boolean.TRUE));
        schema.put(PREFERRED_AIRPORT, Channels.base(() -> ""));
        schema.put(CURRENCY, Channels.base(() -> "INR"));
        SCHEMA = Collections.unmodifiableMap(schema);
    }

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
        input.put(SELECTED_MODEL, firstNonBlank(request.getSelectedModel(), "llama3.2:3b"));
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
        input.put(VALIDATION_ERRORS, new ArrayList<String>());
        input.put(PIPELINE, new ArrayList<AgentStep>());
        input.put(AWAITING_APPROVAL, Boolean.TRUE);
        input.put(CURRENCY, "INR");
        input.put(ITINERARY, new Itinerary());
        input.put(BUDGET_SUMMARY, new BudgetSummary());
        input.put(WEATHER, new WeatherForecast("", "", false));
        return input;
    }

    public String userRequest() {
        return this.<String>value(USER_REQUEST).orElse("");
    }

    public String userId() {
        return this.<String>value(USER_ID).orElse("anonymous");
    }

    public String selectedModel() {
        return this.<String>value(SELECTED_MODEL).orElse("llama3.2:3b");
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

    public String finalPlan() {
        return this.<String>value(FINAL_PLAN).orElse("");
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

    public String preferredAirport() {
        return this.<String>value(PREFERRED_AIRPORT).orElse("");
    }

    public String currency() {
        return this.<String>value(CURRENCY).orElse("INR");
    }

    public long nights() {
        long days = ChronoUnit.DAYS.between(departureDate(), returnDate());
        return Math.max(1, days);
    }

    public boolean overBudget() {
        return budgetSummary() != null && !budgetSummary().isWithinBudget();
    }

    public boolean shouldReplan() {
        return !validationErrors().isEmpty() && retryCount() < maxRetries();
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
        return Map.of(PIPELINE, List.of(new AgentStep(node, status, detail)));
    }

    private static int defaultInt(Integer value, int fallback) {
        return value == null ? fallback : value;
    }
}
