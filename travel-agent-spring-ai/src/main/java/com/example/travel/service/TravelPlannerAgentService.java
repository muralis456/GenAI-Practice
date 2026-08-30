package com.example.travel.service;

import com.example.travel.config.TravelModelsProperties;
import com.example.travel.dto.TravelPlanResponse;
import com.example.travel.dto.TravelRequest;
import com.example.travel.entity.UserPreference;
import com.example.travel.graph.TravelState;
import com.example.travel.model.FlightOption;
import com.example.travel.model.HotelOption;
import com.example.travel.model.Itinerary;
import com.example.travel.model.TravelResearch;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.RunnableConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class TravelPlannerAgentService {

    private static final Logger log = LoggerFactory.getLogger(TravelPlannerAgentService.class);

    private final CompiledGraph<TravelState> travelGraph;
    private final RunnableConfig travelRunnableConfig;
    private final FinalPlannerAgentService finalPlannerAgentService;
    private final UserPreferenceService userPreferenceService;
    private final TravelModelsProperties travelModels;
    private final int maxRetries;
    private final ConcurrentHashMap<String, TravelState> pendingPlans = new ConcurrentHashMap<>();

    public TravelPlannerAgentService(CompiledGraph<TravelState> travelGraph,
                                     RunnableConfig travelRunnableConfig,
                                     FinalPlannerAgentService finalPlannerAgentService,
                                     UserPreferenceService userPreferenceService,
                                     TravelModelsProperties travelModels,
                                     @Value("${travel.graph.max-retries:2}") int maxRetries) {
        this.travelGraph = travelGraph;
        this.travelRunnableConfig = travelRunnableConfig;
        this.finalPlannerAgentService = finalPlannerAgentService;
        this.userPreferenceService = userPreferenceService;
        this.travelModels = travelModels;
        this.maxRetries = maxRetries;
    }

    public TravelPlanResponse createTravelPlan(TravelRequest request, String historyContext) {
        String userId = TravelState.firstNonBlank(request.getUserId(), "anonymous");
        // Unique thread per request so MemorySaver does not append a previous pipeline.
        String threadId = userId + "-" + UUID.randomUUID();
        log.info("Starting LangGraph travel orchestration for userId={}, threadId={}", userId, threadId);

        Map<String, Object> input = TravelState.fromRequest(request, historyContext);
        input.put(TravelState.MAX_RETRIES, maxRetries);
        if (TravelState.isBlank((String) input.get(TravelState.SELECTED_MODEL))) {
            input.put(TravelState.SELECTED_MODEL, configuredModelsLabel());
        }
        userPreferenceService.find(userId).ifPresent(preference -> applyPreferences(input, preference));
        ensureOrigin(input);

        TravelState state = travelGraph.invoke(input, configFor(threadId))
                .orElseThrow(() -> new IllegalStateException("Travel graph produced no final state"));
        pendingPlans.put(threadId, state);
        userPreferenceService.remember(userId, state.originIata(), state.travelStyle(), state.destination());
        return toResponse(state, threadId, true);
    }

    public TravelPlanResponse approve(String userId, String threadId) {
        TravelState state = requirePending(userId, threadId);
        // Final LLM report was already composed after Validator in FinalNode.
        String plan = TravelState.firstNonBlank(state.finalPlan(), finalPlannerAgentService.compose(state));
        TravelPlanResponse response = toResponse(state, threadId, false);
        response.setFinalPlan(plan);
        response.setStatus("COMPLETE");
        response.setAwaitingApproval(false);
        pendingPlans.remove(threadId);
        return response;
    }

    public TravelPlanResponse modify(String userId, String threadId, String notes, String historyContext) {
        TravelState previous = requirePending(userId, threadId);
        TravelRequest request = new TravelRequest();
        request.setUserId(userId);
        request.setDestination(previous.destination());
        request.setDepartureCity(previous.origin());
        request.setDepartureDate(previous.departureDate().toString());
        request.setReturnDate(previous.returnDate().toString());
        request.setAdults(previous.travelers());
        request.setBudget(previous.budget() == null ? previous.budgetLabel() : previous.budget().toPlainString());
        request.setTravelStyle("budget");
        request.setPrompt(previous.userRequest() + " Modify: " + notes);
        request.setSelectedModel(previous.selectedModel());
        Map<String, Object> input = TravelState.fromRequest(request, historyContext);
        input.put(TravelState.MAX_RETRIES, maxRetries);
        input.put(TravelState.REPLAN_NOTES, notes);
        input.put(TravelState.COST_FACTOR, previous.costFactor().multiply(java.math.BigDecimal.valueOf(0.82)));
        input.put(TravelState.ORIGIN_IATA, previous.originIata());
        input.put(TravelState.DESTINATION_IATA, previous.destinationIata());
        ensureOrigin(input);
        String nextThreadId = userId + "-" + UUID.randomUUID();
        TravelState state = travelGraph.invoke(input, configFor(nextThreadId))
                .orElseThrow(() -> new IllegalStateException("Travel graph produced no final state"));
        pendingPlans.remove(threadId);
        pendingPlans.put(nextThreadId, state);
        return toResponse(state, nextThreadId, true);
    }

    private void ensureOrigin(Map<String, Object> input) {
        if (!TravelState.isBlank((String) input.get(TravelState.ORIGIN))) {
            return;
        }
        String preferred = (String) input.get(TravelState.PREFERRED_AIRPORT);
        if (!TravelState.isBlank(preferred)) {
            input.put(TravelState.ORIGIN, preferred);
            return;
        }
        // Sensible India-origin default when the prompt omits "from <city>".
        input.put(TravelState.ORIGIN, "Bengaluru");
        input.put(TravelState.PREFERRED_AIRPORT, "BLR");
    }

    private void applyPreferences(Map<String, Object> input, UserPreference preference) {
        if (TravelState.isBlank((String) input.get(TravelState.ORIGIN)) && preference.getPreferredAirport() != null) {
            input.put(TravelState.ORIGIN, preference.getPreferredAirport());
            input.put(TravelState.PREFERRED_AIRPORT, preference.getPreferredAirport());
        }
        if (preference.getTravelStyle() != null && TravelState.isBlank((String) input.get(TravelState.TRAVEL_STYLE))) {
            input.put(TravelState.TRAVEL_STYLE, preference.getTravelStyle());
        }
        if (preference.getCurrency() != null) {
            input.put(TravelState.CURRENCY, preference.getCurrency());
        }
        String history = (String) input.getOrDefault(TravelState.HISTORY_CONTEXT, "");
        input.put(TravelState.HISTORY_CONTEXT, "Long-term preferences: airport=" + preference.getPreferredAirport()
                + ", style=" + preference.getTravelStyle() + ", currency=" + preference.getCurrency()
                + ", lastDestination=" + preference.getLastDestination()
                + "\n" + history);
    }

    private TravelState requirePending(String userId, String threadId) {
        String key = TravelState.firstNonBlank(threadId, userId);
        TravelState state = pendingPlans.get(key);
        if (state == null) {
            throw new IllegalStateException("No pending plan found for approval. Generate a plan first.");
        }
        return state;
    }

    private RunnableConfig configFor(String threadId) {
        return RunnableConfig.builder(travelRunnableConfig)
                .threadId(threadId)
                .build();
    }

    private TravelPlanResponse toResponse(TravelState state, String threadId, boolean awaitingApproval) {
        TravelPlanResponse response = new TravelPlanResponse(
                state.userId(),
                TravelState.firstNonBlank(state.destination(), "Destination from current request"),
                TravelState.firstNonBlank(state.selectedModel(), configuredModelsLabel()),
                state.finalPlan(),
                joinFlights(state),
                joinResearch(state),
                joinHotels(state),
                joinItinerary(state)
        );
        response.setThreadId(threadId);
        response.setAwaitingApproval(awaitingApproval);
        response.setStatus(awaitingApproval ? "PENDING_APPROVAL" : "COMPLETE");
        response.setRouteSummary(TravelState.firstNonBlank(state.origin(), "?")
                + " (" + TravelState.firstNonBlank(state.originIata(), "?") + ") → "
                + TravelState.firstNonBlank(state.destination(), "?")
                + " (" + TravelState.firstNonBlank(state.destinationIata(), "?") + ")"
                + " · " + state.departureDate() + " to " + state.returnDate()
                + " · " + state.nights() + " nights");
        response.setBudgetSummary(state.budgetSummary());
        response.setPipeline(state.pipeline());
        response.setValidationErrors(state.validationErrors());
        return response;
    }

    private String configuredModelsLabel() {
        return "planner=" + travelModels.getPlanner()
                + "; extract=" + travelModels.getExtraction()
                + "; itinerary=" + travelModels.getItinerary()
                + "; final=" + travelModels.getFinale();
    }

    private String joinFlights(TravelState state) {
        if (state.flights().isEmpty()) {
            return "Not requested for this trip.";
        }
        return state.flights().stream().map(FlightOption::toDisplay).collect(Collectors.joining("\n"));
    }

    private String joinResearch(TravelState state) {
        StringBuilder sb = new StringBuilder();
        if (!state.research().isEmpty()) {
            sb.append(state.research().stream().map(TravelResearch::toDisplay).collect(Collectors.joining("\n")));
        }
        if (!state.attractions().isEmpty()) {
            if (!sb.isEmpty()) {
                sb.append("\n\nAttractions\n");
            } else {
                sb.append("Attractions\n");
            }
            sb.append(state.attractions().stream()
                    .map(attraction -> attraction.toDisplay())
                    .collect(Collectors.joining("\n")));
        }
        return sb.isEmpty() ? "Not requested for this trip." : sb.toString();
    }

    private String joinHotels(TravelState state) {
        if (state.hotels().isEmpty()) {
            return "Not requested for this trip.";
        }
        return state.hotels().stream().map(HotelOption::toDisplay).collect(Collectors.joining("\n"));
    }

    private String joinItinerary(TravelState state) {
        Itinerary itinerary = state.itinerary();
        if (itinerary == null || itinerary.isEmpty()) {
            return "Not requested for this trip.";
        }
        return itinerary.toDisplay();
    }
}
