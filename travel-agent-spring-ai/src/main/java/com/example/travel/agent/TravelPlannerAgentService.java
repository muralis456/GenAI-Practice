package com.example.travel.agent;

import com.example.travel.config.TravelModelsProperties;
import com.example.travel.dto.TravelPlanResponse;
import com.example.travel.dto.TravelRequest;
import com.example.travel.entity.UserPreference;
import com.example.travel.graph.TravelGraphNodes;
import com.example.travel.graph.TravelState;
import com.example.travel.model.FlightOption;
import com.example.travel.model.HotelOption;
import com.example.travel.model.Itinerary;
import com.example.travel.model.TravelResearch;
import com.example.travel.service.UserPreferenceService;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.checkpoint.PostgresSaver;
import org.bsc.langgraph4j.state.StateSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Drives the LangGraph travel workflow. HITL approve/modify resume the same
 * {@code threadId} from PostgreSQL checkpoints — no in-memory pending map.
 */
@Service
public class TravelPlannerAgentService {

    private static final Logger log = LoggerFactory.getLogger(TravelPlannerAgentService.class);

    private final CompiledGraph<TravelState> travelGraph;
    private final RunnableConfig travelRunnableConfig;
    private final PostgresSaver travelCheckpointSaver;
    private final FinalPlannerAgentService finalPlannerAgentService;
    private final UserPreferenceService userPreferenceService;
    private final TravelModelsProperties travelModels;
    private final int maxRetries;

    public TravelPlannerAgentService(CompiledGraph<TravelState> travelGraph,
                                     RunnableConfig travelRunnableConfig,
                                     PostgresSaver travelCheckpointSaver,
                                     FinalPlannerAgentService finalPlannerAgentService,
                                     UserPreferenceService userPreferenceService,
                                     TravelModelsProperties travelModels,
                                     @Value("${travel.graph.max-retries:2}") int maxRetries) {
        this.travelGraph = travelGraph;
        this.travelRunnableConfig = travelRunnableConfig;
        this.travelCheckpointSaver = travelCheckpointSaver;
        this.finalPlannerAgentService = finalPlannerAgentService;
        this.userPreferenceService = userPreferenceService;
        this.travelModels = travelModels;
        this.maxRetries = maxRetries;
    }

    public TravelPlanResponse createTravelPlan(TravelRequest request, String historyContext) {
        String userId = TravelState.firstNonBlank(request.getUserId(), "anonymous");
        String threadId = userId + "-" + UUID.randomUUID();
        log.info("Starting LangGraph travel orchestration for userId={}, threadId={}", userId, threadId);

        Map<String, Object> input = TravelState.fromRequest(request, historyContext);
        input.put(TravelState.MAX_RETRIES, maxRetries);
        if (TravelState.isBlank((String) input.get(TravelState.SELECTED_MODEL))) {
            input.put(TravelState.SELECTED_MODEL, configuredModelsLabel());
        }
        userPreferenceService.find(userId).ifPresent(preference -> applyPreferences(input, preference));
        ensureOrigin(input);

        RunnableConfig config = configFor(threadId);
        travelGraph.invoke(input, config);
        TravelState state = requireCheckpointState(threadId);
        userPreferenceService.remember(userId, state.originIata(), state.travelStyle(), state.destination());
        boolean pending = isAwaitingHitl(threadId);
        log.info("Graph paused for HITL={} threadId={}", pending, threadId);
        return toResponse(state, threadId, pending);
    }

    public TravelPlanResponse approve(String userId, String threadId) {
        String key = TravelState.firstNonBlank(threadId, userId);
        requireCheckpointState(key);
        RunnableConfig config = configFor(key);

        Map<String, Object> decision = new LinkedHashMap<>();
        decision.put(TravelState.HITL_DECISION, "approve");
        decision.put(TravelState.AWAITING_APPROVAL, Boolean.FALSE);

        TravelState state = travelGraph.invoke(GraphInput.resume(decision), config)
                .orElseGet(() -> requireCheckpointState(key));
        try {
            travelCheckpointSaver.release(config);
        } catch (Exception ex) {
            log.warn("Could not release checkpoint threadId={}", key, ex);
        }

        String plan = TravelState.firstNonBlank(state.finalPlan(), finalPlannerAgentService.compose(state));
        TravelPlanResponse response = toResponse(state, key, false);
        response.setFinalPlan(plan);
        response.setStatus("COMPLETE");
        response.setAwaitingApproval(false);
        return response;
    }

    public TravelPlanResponse modify(String userId, String threadId, String notes, String historyContext) {
        String key = TravelState.firstNonBlank(threadId, userId);
        TravelState previous = requireCheckpointState(key);
        RunnableConfig config = configFor(key);

        Map<String, Object> decision = new LinkedHashMap<>();
        decision.put(TravelState.HITL_DECISION, "modify");
        decision.put(TravelState.AWAITING_APPROVAL, Boolean.TRUE);
        decision.put(TravelState.REPLAN_NOTES, TravelState.firstNonBlank(notes, "Please make it cheaper"));
        decision.put(TravelState.TRAVEL_STYLE, "budget");
        decision.put(TravelState.COST_FACTOR,
                previous.costFactor().multiply(BigDecimal.valueOf(0.82)));
        if (!TravelState.isBlank(historyContext)) {
            decision.put(TravelState.HISTORY_CONTEXT, historyContext);
        }

        log.info("Resuming graph for MODIFY on same threadId={}", key);
        travelGraph.invoke(GraphInput.resume(decision), config);
        TravelState state = requireCheckpointState(key);
        boolean pending = isAwaitingHitl(key);
        return toResponse(state, key, pending);
    }

    private TravelState requireCheckpointState(String threadId) {
        try {
            StateSnapshot<TravelState> snapshot = travelGraph.getState(configFor(threadId));
            if (snapshot == null || snapshot.state() == null) {
                throw new IllegalStateException("No graph checkpoint for threadId=" + threadId
                        + ". Generate a plan first (or the thread expired).");
            }
            return snapshot.state();
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("No graph checkpoint for threadId=" + threadId
                    + ". Generate a plan first (checkpoints survive restart via PostgreSQL).", ex);
        }
    }

    private boolean isAwaitingHitl(String threadId) {
        try {
            StateSnapshot<TravelState> snapshot = travelGraph.getState(configFor(threadId));
            if (snapshot == null) {
                return false;
            }
            String next = snapshot.next();
            return TravelGraphNodes.HITL.equals(next)
                    || Boolean.TRUE.equals(snapshot.state().awaitingApproval())
                    && !TravelGraphNodes.COMPLETE.equals(next)
                    && !org.bsc.langgraph4j.StateGraph.END.equals(next);
        } catch (Exception ex) {
            log.debug("Could not read HITL status for threadId={}", threadId, ex);
            return false;
        }
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
        response.setSources(state.provenance().stream().map(event -> event.toDisplay()).toList());
        return response;
    }

    private String configuredModelsLabel() {
        return "planner=" + travelModels.getPlanner()
                + "; extract=" + travelModels.getExtraction()
                + "; itinerary=" + travelModels.getItinerary()
                + "; final=" + travelModels.getFinale();
    }

    private String joinFlights(TravelState state) {
        if (!state.needsFlights()) {
            return "Not requested for this query.";
        }
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
        if (!state.needsHotels()) {
            return "Not requested for this query.";
        }
        if (state.hotels().isEmpty()) {
            return "Not requested for this trip.";
        }
        return state.hotels().stream().map(HotelOption::toDisplay).collect(Collectors.joining("\n"));
    }

    private String joinItinerary(TravelState state) {
        if (!state.needsItinerary()) {
            return "Not requested for this query.";
        }
        Itinerary itinerary = state.itinerary();
        if (itinerary == null || itinerary.isEmpty()) {
            return "Not requested for this trip.";
        }
        return itinerary.toDisplay();
    }
}
