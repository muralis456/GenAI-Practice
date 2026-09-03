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
import com.example.travel.model.ModificationRequest;
import com.example.travel.model.TravelResearch;
import com.example.travel.service.AgentExecutionBudget;
import com.example.travel.service.ConversationMemoryService;
import com.example.travel.service.GraphProgressHub;
import com.example.travel.service.GraphRunContext;
import com.example.travel.service.ModelRoutingContext;
import com.example.travel.service.UserPreferenceService;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.NodeOutput;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.checkpoint.PostgresSaver;
import org.bsc.langgraph4j.state.StateSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
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
    private final ModificationAgentService modificationAgentService;
    private final AgentExecutionBudget executionBudget;
    private final GraphProgressHub graphProgressHub;
    private final GraphRunContext graphRunContext;
    private final ExecutorService travelPlanExecutor;
    private final ConversationMemoryService conversationMemoryService;
    private final int maxRetries;

    public TravelPlannerAgentService(CompiledGraph<TravelState> travelGraph,
                                     RunnableConfig travelRunnableConfig,
                                     PostgresSaver travelCheckpointSaver,
                                     FinalPlannerAgentService finalPlannerAgentService,
                                     UserPreferenceService userPreferenceService,
                                     TravelModelsProperties travelModels,
                                     ModificationAgentService modificationAgentService,
                                     AgentExecutionBudget executionBudget,
                                     GraphProgressHub graphProgressHub,
                                     GraphRunContext graphRunContext,
                                     @org.springframework.beans.factory.annotation.Qualifier("travelPlanExecutor") ExecutorService travelPlanExecutor,
                                     ConversationMemoryService conversationMemoryService,
                                     @Value("${travel.graph.max-retries:2}") int maxRetries) {
        this.travelGraph = travelGraph;
        this.travelRunnableConfig = travelRunnableConfig;
        this.travelCheckpointSaver = travelCheckpointSaver;
        this.finalPlannerAgentService = finalPlannerAgentService;
        this.userPreferenceService = userPreferenceService;
        this.travelModels = travelModels;
        this.modificationAgentService = modificationAgentService;
        this.executionBudget = executionBudget;
        this.graphProgressHub = graphProgressHub;
        this.graphRunContext = graphRunContext;
        this.travelPlanExecutor = travelPlanExecutor;
        this.conversationMemoryService = conversationMemoryService;
        this.maxRetries = maxRetries;
    }

    public TravelPlanResponse createTravelPlan(TravelRequest request, String historyContext) {
        String userId = TravelState.firstNonBlank(request.getUserId(), "anonymous");
        String threadId = userId + "-" + UUID.randomUUID();
        log.info("Starting LangGraph travel orchestration for userId={}, threadId={}", userId, threadId);

        Map<String, Object> input = TravelState.fromRequest(request, historyContext);
        input.put(TravelState.MAX_RETRIES, maxRetries);
        input.put(TravelState.GRAPH_THREAD_ID, threadId);
        String policy = ModelRoutingContext.normalize(request.getSelectedModel());
        input.put(TravelState.SELECTED_MODEL, policy);
        input.put(TravelState.MODEL_POLICY, policy);
        userPreferenceService.find(userId).ifPresent(preference -> applyPreferences(input, preference));
        ensureOrigin(input);

        RunnableConfig config = configFor(threadId);
        ModelRoutingContext.set(policy);
        executionBudget.begin();
        graphRunContext.open(threadId, executionBudget.capture(), policy);
        try {
            travelGraph.invoke(input, config);
        } finally {
            graphRunContext.close(threadId);
            executionBudget.end();
            ModelRoutingContext.clear();
        }
        TravelState state = requireCheckpointState(threadId);
        userPreferenceService.remember(userId, state.originIata(), state.travelStyle(), state.destination());
        boolean pending = isAwaitingHitl(threadId);
        log.info("Graph paused for HITL={} threadId={}", pending, threadId);
        return toResponse(state, threadId, pending);
    }

    public String startTravelPlan(TravelRequest request, String historyContext) {
        String userId = TravelState.firstNonBlank(request.getUserId(), "anonymous");
        String threadId = userId + "-" + UUID.randomUUID();
        Map<String, Object> input = TravelState.fromRequest(request, historyContext);
        input.put(TravelState.MAX_RETRIES, maxRetries);
        input.put(TravelState.GRAPH_THREAD_ID, threadId);
        String policy = ModelRoutingContext.normalize(request.getSelectedModel());
        input.put(TravelState.SELECTED_MODEL, policy);
        input.put(TravelState.MODEL_POLICY, policy);
        userPreferenceService.find(userId).ifPresent(preference -> applyPreferences(input, preference));
        ensureOrigin(input);
        graphProgressHub.open(threadId);
        travelPlanExecutor.submit(() -> runStreaming(threadId, userId, policy, input));
        return threadId;
    }

    private void runStreaming(String threadId, String userId, String policy, Map<String, Object> input) {
        RunnableConfig config = configFor(threadId);
        ModelRoutingContext.set(policy);
        executionBudget.begin();
        graphRunContext.open(threadId, executionBudget.capture(), policy);
        try {
            graphProgressHub.emit(threadId, "started", Map.of("threadId", threadId, "node", "START"));
            for (NodeOutput<TravelState> output : travelGraph.stream(input, config)) {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("node", output.node());
                payload.put("end", output.isEND());
                if (output.state() != null) {
                    payload.put("pipeline", output.state().pipeline());
                }
                graphProgressHub.emit(threadId, "node", payload);
            }
            TravelState state = requireCheckpointState(threadId);
            userPreferenceService.remember(userId, state.originIata(), state.travelStyle(), state.destination());
            boolean pending = isAwaitingHitl(threadId);
            TravelPlanResponse plan = toResponse(state, threadId, pending);
            conversationMemoryService.saveUiMessage(userId, userId, "assistant",
                    plan.getFinalPlan() != null ? plan.getFinalPlan()
                            : (plan.getRouteSummary() != null ? plan.getRouteSummary() : "Plan ready"));
            Map<String, Object> done = new LinkedHashMap<>();
            done.put("plan", plan);
            graphProgressHub.emit(threadId, "complete", done);
        } catch (Exception ex) {
            log.warn("Streaming plan failed threadId={}", threadId, ex);
            graphProgressHub.emit(threadId, "failed", Map.of("error",
                    ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()));
        } finally {
            graphRunContext.close(threadId);
            executionBudget.end();
            ModelRoutingContext.clear();
        }
    }

    public TravelPlanResponse approve(String userId, String threadId) {
        String key = requireOwnedThread(userId, threadId);
        requireCheckpointState(key);
        RunnableConfig config = configFor(key);

        Map<String, Object> decision = new LinkedHashMap<>();
        decision.put(TravelState.HITL_DECISION, "approve");
        decision.put(TravelState.AWAITING_APPROVAL, Boolean.FALSE);

        ModelRoutingContext.set(previousPolicy(key));
        executionBudget.begin();
        graphRunContext.open(key, executionBudget.capture(), previousPolicy(key));
        TravelState state;
        try {
            state = travelGraph.invoke(GraphInput.resume(decision), config)
                    .orElseGet(() -> requireCheckpointState(key));
        } finally {
            graphRunContext.close(key);
            executionBudget.end();
            ModelRoutingContext.clear();
        }
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
        String key = requireOwnedThread(userId, threadId);
        TravelState previous = requireCheckpointState(key);
        RunnableConfig config = configFor(key);

        ModificationRequest modification = modificationAgentService.interpret(previous, notes);

        Map<String, Object> decision = new LinkedHashMap<>();
        decision.put(TravelState.HITL_DECISION, "modify");
        decision.put(TravelState.AWAITING_APPROVAL, Boolean.TRUE);
        decision.put(TravelState.REPLAN_NOTES, TravelState.firstNonBlank(notes, modification.getNotes()));
        decision.put(TravelState.MODIFICATION, modification);
        if (modification.isAddDestination() && !TravelState.isBlank(modification.getDestination())) {
            decision.put(TravelState.DESTINATION, previous.destination() + " and " + modification.getDestination());
            decision.put(TravelState.NEEDS_RESEARCH, Boolean.TRUE);
            decision.put(TravelState.NEEDS_ITINERARY, Boolean.TRUE);
        }
        if (modification.isHotelUpgrade()) {
            decision.put(TravelState.HOTEL_CHEAPER, Boolean.FALSE);
        }
        if (modification.isReduceCost()) {
            decision.put(TravelState.HOTEL_CHEAPER, Boolean.TRUE);
        }
        if (!TravelState.isBlank(modification.getFlightPreference())) {
            decision.put(TravelState.FLIGHT_PREFERENCE, modification.getFlightPreference());
        }
        if (modification.getHotelBudget() != null) {
            decision.put(TravelState.BUDGET, modification.getHotelBudget());
        }
        if (!TravelState.isBlank(historyContext)) {
            decision.put(TravelState.HISTORY_CONTEXT, historyContext);
        }

        log.info("Resuming graph for MODIFY type={} on same threadId={}", modification.getChangeType(), key);
        ModelRoutingContext.set(previous.modelPolicy());
        executionBudget.begin();
        graphRunContext.open(key, executionBudget.capture(), previous.modelPolicy());
        try {
            travelGraph.invoke(GraphInput.resume(decision), config);
        } finally {
            graphRunContext.close(key);
            executionBudget.end();
            ModelRoutingContext.clear();
        }
        TravelState state = requireCheckpointState(key);
        boolean pending = isAwaitingHitl(key);
        return toResponse(state, key, pending);
    }

    public TravelPlanResponse reject(String userId, String threadId) {
        String key = requireOwnedThread(userId, threadId);
        requireCheckpointState(key);
        RunnableConfig config = configFor(key);
        Map<String, Object> decision = new LinkedHashMap<>();
        decision.put(TravelState.HITL_DECISION, "reject");
        decision.put(TravelState.AWAITING_APPROVAL, Boolean.FALSE);
        ModelRoutingContext.set(previousPolicy(key));
        executionBudget.begin();
        graphRunContext.open(key, executionBudget.capture(), previousPolicy(key));
        TravelState state;
        try {
            state = travelGraph.invoke(GraphInput.resume(decision), config)
                    .orElseGet(() -> requireCheckpointState(key));
        } finally {
            graphRunContext.close(key);
            executionBudget.end();
            ModelRoutingContext.clear();
        }
        try {
            travelCheckpointSaver.release(config);
        } catch (Exception ex) {
            log.warn("Could not release checkpoint threadId={}", key, ex);
        }
        TravelPlanResponse response = toResponse(state, key, false);
        response.setStatus("REJECTED");
        response.setAwaitingApproval(false);
        return response;
    }

    public Map<String, Object> history(String threadId) {
        String key = TravelState.firstNonBlank(threadId);
        StateSnapshot<TravelState> snapshot = travelGraph.getState(configFor(key));
        TravelState state = snapshot.state();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("threadId", key);
        body.put("next", snapshot.next());
        body.put("pipeline", state.pipeline());
        body.put("modelPolicy", state.modelPolicy());
        body.put("intentConfidence", state.intentConfidence());
        body.put("validationErrors", state.validationErrors());
        body.put("semanticNotes", state.semanticNotes());
        if (state.planQuality() != null) {
            body.put("planQuality", state.planQuality());
        }
        if (state.nodeFailure() != null && !TravelState.isBlank(state.nodeFailure().getLastFailedNode())) {
            body.put("nodeFailure", state.nodeFailure());
        }
        body.put("executionTimeline", state.pipeline());
        try {
            List<Map<String, Object>> snapshots = new ArrayList<>();
            for (StateSnapshot<TravelState> item : travelGraph.getStateHistory(configFor(key))) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("node", item.node());
                row.put("next", item.next());
                if (item.state() != null) {
                    row.put("pipeline", item.state().pipeline());
                    row.put("supervisorDecision", item.state().supervisorDecision());
                    row.put("dispatchRoute", item.state().dispatchRoute());
                    if (item.state().planQuality() != null) {
                        row.put("planQuality", item.state().planQuality().getOverall());
                    }
                }
                snapshots.add(row);
            }
            body.put("snapshots", snapshots);
        } catch (Exception ex) {
            log.debug("Could not load state history for threadId={}", key, ex);
            body.put("snapshots", List.of());
        }
        return body;
    }

    private String requireOwnedThread(String userId, String threadId) {
        String key = TravelState.firstNonBlank(threadId, userId);
        String requester = TravelState.firstNonBlank(userId, "anonymous");
        if (!key.equals(requester) && !key.startsWith(requester + "-")) {
            throw new IllegalArgumentException("Thread " + key + " is not owned by user " + requester);
        }
        TravelState state = requireCheckpointState(key);
        String owner = TravelState.firstNonBlank(state.userId(), requester);
        if (!owner.equals(requester)) {
            throw new IllegalArgumentException("Thread " + key + " belongs to " + owner + ", not " + requester);
        }
        return key;
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

    private String previousPolicy(String threadId) {
        try {
            return requireCheckpointState(threadId).modelPolicy();
        } catch (Exception ignored) {
            return "BALANCED";
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
                TravelState.firstNonBlank(state.modelPolicy(), configuredModelsLabel()),
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
        response.setPlanQuality(state.planQuality());
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
