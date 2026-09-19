package com.example.travel.agent;

import com.example.travel.config.TravelModelsProperties;
import com.example.travel.dto.AgentExecutionDetails;
import com.example.travel.dto.TripPlanResult;
import com.example.travel.dto.TravelPlanResponse;
import com.example.travel.dto.TravelRequest;
import com.example.travel.entity.UserPreference;
import com.example.travel.graph.GraphExecutionLogger;
import com.example.travel.graph.TravelGraphNodes;
import com.example.travel.graph.TravelState;
import com.example.travel.model.ModificationRequest;
import com.example.travel.service.AgentExecutionBudget;
import com.example.travel.service.ConversationMemoryService;
import com.example.travel.service.GraphProgressHub;
import com.example.travel.service.GraphRunContext;
import com.example.travel.service.ModelRoutingContext;
import com.example.travel.service.TripPlanAssembler;
import com.example.travel.service.UserPreferenceService;
import tools.jackson.databind.ObjectMapper;

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

/**
 * Drives the LangGraph travel workflow.
 *
 * HITL approve/modify resume the same threadId from PostgreSQL checkpoints.
 * No in-memory pending map is used.
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
    private final TripPlanAssembler tripPlanAssembler;
    private final com.example.travel.service.TripHistoryService tripHistoryService;
    private final ObjectMapper objectMapper;
    private final int maxRetries;

    public TravelPlannerAgentService(
            CompiledGraph<TravelState> travelGraph,
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
            TripPlanAssembler tripPlanAssembler,
            com.example.travel.service.TripHistoryService tripHistoryService,
            ObjectMapper objectMapper,
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
        this.tripPlanAssembler = tripPlanAssembler;
        this.tripHistoryService = tripHistoryService;
        this.objectMapper = objectMapper;
        this.maxRetries = maxRetries;
    }

    public TravelPlanResponse createTravelPlan(
            TravelRequest request,
            String historyContext) {

        String userId = TravelState.firstNonBlank(
                request.getUserId(),
                "anonymous");

        String threadId = userId + "-" + UUID.randomUUID();

        log.info(
                "Starting LangGraph travel orchestration for userId={}, threadId={}",
                userId,
                threadId);

        Map<String, Object> input = TravelState.fromRequest(request, historyContext);

        input.put(TravelState.MAX_RETRIES, maxRetries);
        input.put(TravelState.GRAPH_THREAD_ID, threadId);

        String policy = ModelRoutingContext.normalize(request.getSelectedModel());

        input.put(TravelState.SELECTED_MODEL, policy);
        input.put(TravelState.MODEL_POLICY, policy);

        userPreferenceService
                .find(userId)
                .ifPresent(preference -> applyPreferences(input, preference));

        ensureOrigin(input);

        RunnableConfig config = configFor(threadId);

        ModelRoutingContext.set(policy);
        executionBudget.begin();
        graphRunContext.open(
                threadId,
                executionBudget.capture(),
                policy);

        long runStarted = System.currentTimeMillis();
        GraphExecutionLogger.runStart(threadId, userId,
                String.valueOf(input.get(TravelState.REQUEST_TYPE)),
                String.valueOf(input.get(TravelState.DESTINATION)),
                String.valueOf(input.get(TravelState.ORIGIN)),
                String.valueOf(input.get(TravelState.DEPARTURE_DATE)),
                String.valueOf(input.get(TravelState.RETURN_DATE)),
                ((Number) input.getOrDefault(TravelState.TRAVELERS, 1)).intValue(),
                String.valueOf(input.get(TravelState.BUDGET_LABEL)), policy);
        try {
            travelGraph.invoke(input, config);
        } finally {
            graphRunContext.close(threadId);
            executionBudget.end();
            ModelRoutingContext.clear();
        }

        TravelState state = requireCheckpointState(threadId);

        userPreferenceService.remember(
                userId,
                state.originIata(),
                state.travelStyle(),
                state.destination());

        boolean pending = isAwaitingHitl(threadId);

        GraphExecutionLogger.runComplete(state, System.currentTimeMillis() - runStarted, pending);
        log.info(
                "Graph paused for HITL={} threadId={}",
                pending,
                threadId);

        return toResponse(
                state,
                threadId,
                pending);
    }

    public String startTravelPlan(
            TravelRequest request,
            String historyContext) {

        String userId = TravelState.firstNonBlank(
                request.getUserId(),
                "anonymous");

        String threadId = userId + "-" + UUID.randomUUID();
        String conversationId = TravelState.firstNonBlank(request.getConversationId(), threadId);

        Map<String, Object> input = TravelState.fromRequest(request, historyContext);

        input.put(TravelState.MAX_RETRIES, maxRetries);
        input.put(TravelState.GRAPH_THREAD_ID, threadId);

        String policy = ModelRoutingContext.normalize(request.getSelectedModel());

        input.put(TravelState.SELECTED_MODEL, policy);
        input.put(TravelState.MODEL_POLICY, policy);

        userPreferenceService
                .find(userId)
                .ifPresent(preference -> applyPreferences(input, preference));

        ensureOrigin(input);

        graphProgressHub.open(threadId);

        // Bind the first user message to the new graph thread. This makes each
        // trip independently discoverable in the database and avoids races
        // between the async graph and conversation persistence.
        String query = TravelState.firstNonBlank(request.getPrompt(), request.getPreferences());
        conversationMemoryService.saveMessage(userId, threadId, conversationId, "user", query);

        GraphExecutionLogger.runContext(
                "plan-start",
                threadId,
                policy);

        travelPlanExecutor.submit(
                () -> runStreaming(
                        threadId,
                        userId,
                        conversationId,
                        policy,
                        input));

        return threadId;
    }

    private void runStreaming(
            String threadId,
            String userId,
            String conversationId,
            String policy,
            Map<String, Object> input) {

        RunnableConfig config = configFor(threadId);

        ModelRoutingContext.set(policy);
        executionBudget.begin();

        graphRunContext.open(
                threadId,
                executionBudget.capture(),
                policy);

        long runStarted = System.currentTimeMillis();
        GraphExecutionLogger.runStart(threadId, userId,
                String.valueOf(input.get(TravelState.REQUEST_TYPE)),
                String.valueOf(input.get(TravelState.DESTINATION)),
                String.valueOf(input.get(TravelState.ORIGIN)),
                String.valueOf(input.get(TravelState.DEPARTURE_DATE)),
                String.valueOf(input.get(TravelState.RETURN_DATE)),
                ((Number) input.getOrDefault(TravelState.TRAVELERS, 1)).intValue(),
                String.valueOf(input.get(TravelState.BUDGET_LABEL)), policy);
        try {

            graphProgressHub.emit(
                    threadId,
                    "started",
                    Map.of(
                            "threadId",
                            threadId,
                            "node",
                            "START"));

            for (NodeOutput<TravelState> output : travelGraph.stream(input, config)) {

                GraphExecutionLogger.streamTransition(
                        threadId,
                        output.node(),
                        output.isEND());

                Map<String, Object> payload = new LinkedHashMap<>();

                payload.put("node", output.node());
                payload.put("end", output.isEND());

                if (output.state() != null) {
                    payload.put(
                            "pipeline",
                            output.state().pipeline());
                }

                graphProgressHub.emit(
                        threadId,
                        "node",
                        payload);
            }

            TravelState state = requireCheckpointState(threadId);

            userPreferenceService.remember(
                    userId,
                    state.originIata(),
                    state.travelStyle(),
                    state.destination());

            boolean pending = isAwaitingHitl(threadId);

            TravelPlanResponse plan = toResponse(
                    state,
                    threadId,
                    pending,
                    "");

            conversationMemoryService.saveUiMessage(
                    userId,
                    threadId,
                    conversationId,
                    "assistant",
                    plan.getPlan() != null
                            && plan.getPlan().getTrip() != null
                                    ? TravelState.firstNonBlank(
                                            plan.getPlan().getTrip().getTitle(),
                                            "Plan ready")
                                    : "Plan ready",
                    plan);
            tripHistoryService.saveOrUpdate(userId, plan);

            GraphExecutionLogger.runComplete(state, System.currentTimeMillis() - runStarted, pending);

            Map<String, Object> done = new LinkedHashMap<>();

            done.put("plan", plan);

            graphProgressHub.emit(
                    threadId,
                    "complete",
                    done);

        } catch (Exception ex) {

            GraphExecutionLogger.runFailed(threadId, System.currentTimeMillis() - runStarted, ex);
            log.warn(
                    "Streaming plan failed threadId={}",
                    threadId,
                    ex);

            // Never send exception messages, class names, or stack-trace details
            // to the browser. Full diagnostics stay in server logs only.
            graphProgressHub.emit(
                    threadId,
                    "failed",
                    Map.of(
                            "error",
                            "We couldn't complete your travel plan. Please try again."));

        } finally {

            graphRunContext.close(threadId);
            executionBudget.end();
            ModelRoutingContext.clear();
        }
    }

    /**
     * Rebuilds the latest structured response for a completed/recent thread.
     * This is used by Recent History when an older conversation-memory row
     * does not yet contain structuredData.
     */
    public TravelPlanResponse restore(String userId, String threadId) {
        String key = requireOwnedThread(userId, threadId);
        TravelState state = requireCheckpointState(key);
        return toResponse(state, key, isAwaitingHitl(key), "");
    }

    public TravelPlanResponse approve(
            String userId,
            String threadId) {

        String key = requireOwnedThread(
                userId,
                threadId);

        TravelState current = requireCheckpointState(key);
        if (current.userInputRequired()) {
            throw new IllegalStateException("This plan is waiting for the missing travel details. Use Modify to provide them.");
        }

        RunnableConfig config = configFor(key);

        Map<String, Object> decision = new LinkedHashMap<>();

        decision.put(
                TravelState.HITL_DECISION,
                "approve");

        decision.put(
                TravelState.AWAITING_APPROVAL,
                Boolean.FALSE);

        ModelRoutingContext.set(
                previousPolicy(key));

        executionBudget.begin();

        graphRunContext.open(
                key,
                executionBudget.capture(),
                previousPolicy(key));

        TravelState state;

        try {

            state = travelGraph.invoke(
                    GraphInput.resume(decision),
                    config)
                    .orElseGet(
                            () -> requireCheckpointState(key));

        } finally {

            graphRunContext.close(key);
            executionBudget.end();
            ModelRoutingContext.clear();
        }

        try {

            travelCheckpointSaver.release(config);

        } catch (Exception ex) {

            log.warn(
                    "Could not release checkpoint threadId={}",
                    key,
                    ex);
        }

        TravelPlanResponse response = toResponse(
                state,
                key,
                false);

        String tips = finalPlannerAgentService.buildTips(state);

        response.getPlan().setTips(tips);

        response.setStatus("COMPLETE");
        response.setAwaitingApproval(false);
        response.getPlan().getTrip().setStatus("COMPLETE");
        response.getPlan().getTrip().setAwaitingApproval(false);
        tripHistoryService.saveOrUpdate(userId, response);

        return response;
    }

    /**
     * Resume an existing graph checkpoint with a user modification.
     *
     * IMPORTANT:
     * The checkpoint contains the original USER_REQUEST.
     * A modification is a new user request, so USER_REQUEST must
     * explicitly be replaced in the resumed graph state.
     */
    public TravelPlanResponse modify(
            String userId,
            String threadId,
            String notes,
            String historyContext) {

        String key = requireOwnedThread(
                userId,
                threadId);

        TravelState previous = requireCheckpointState(key);

        RunnableConfig config = configFor(key);

        /*
         * Interpret the latest user modification using the
         * existing travel state as context.
         */
        ModificationRequest modification = modificationAgentService.interpret(
                previous,
                notes);

        String latestUserRequest;
        if (previous.userInputRequired()) {
            // A clarification answer such as "Bengaluru" is not a standalone
            // intent. Preserve the original goal and attach the user's missing
            // detail so the next planning pass can resolve the slot correctly.
            latestUserRequest = previous.userRequest()
                    + "\nUser supplied missing detail: "
                    + TravelState.firstNonBlank(notes, modification.getNotes());
        } else {
            latestUserRequest = TravelState.firstNonBlank(notes, modification.getNotes());
        }

        Map<String, Object> decision = new LinkedHashMap<>();

        decision.put(
                TravelState.HITL_DECISION,
                "modify");

        decision.put(
                TravelState.AWAITING_APPROVAL,
                Boolean.TRUE);
        decision.put(TravelState.USER_INPUT_REQUIRED, Boolean.FALSE);
        decision.put(TravelState.USER_INPUT_QUESTION, "");

        /*
         * ============================================================
         * CRITICAL FIX
         * ============================================================
         *
         * The graph is resumed using the existing checkpoint.
         *
         * Therefore TravelState.USER_REQUEST still contains the
         * ORIGINAL request unless we explicitly overwrite it here.
         *
         * ReplanAgentService uses state.userRequest() to understand
         * what the user is asking for now.
         *
         * Example:
         *
         * Original:
         * "best hotels in Mumbai under 20k budget"
         *
         * New:
         * "also include flight details from Bangalore to Mumbai"
         *
         * Without this update the ReplanAgent receives the old
         * hotel request and cannot intelligently select
         * GET_FLIGHT_DETAILS.
         */
        decision.put(
                TravelState.USER_REQUEST,
                latestUserRequest);

        /*
         * Keep REPLAN_NOTES as well because other parts of the
         * replan flow use this field for modification information.
         */
        decision.put(
                TravelState.REPLAN_NOTES,
                latestUserRequest);

        decision.put(
                TravelState.MODIFICATION,
                modification);

        /*
         * ADD DESTINATION
         */
        if (modification.isAddDestination()
                && !TravelState.isBlank(
                        modification.getDestination())) {

            decision.put(
                    TravelState.DESTINATION,
                    previous.destination()
                            + " and "
                            + modification.getDestination());

            decision.put(
                    TravelState.NEEDS_RESEARCH,
                    Boolean.TRUE);

            decision.put(
                    TravelState.NEEDS_ITINERARY,
                    Boolean.TRUE);
        }

        /*
         * HOTEL UPGRADE
         */
        if (modification.isHotelUpgrade()) {

            decision.put(
                    TravelState.HOTEL_CHEAPER,
                    Boolean.FALSE);
        }

        /*
         * REDUCE COST
         */
        if (modification.isReduceCost()) {

            decision.put(
                    TravelState.HOTEL_CHEAPER,
                    Boolean.TRUE);
        }

        /*
         * FLIGHT PREFERENCE
         */
        if (!TravelState.isBlank(
                modification.getFlightPreference())) {

            decision.put(
                    TravelState.FLIGHT_PREFERENCE,
                    modification.getFlightPreference());
        }

        /*
         * HOTEL / BUDGET
         */
        if (modification.getHotelBudget() != null) {
            // A hotel ceiling is a hotel-search constraint, not the overall trip
            // budget. Keep the original trip budget untouched so a modification
            // cannot accidentally turn "hotel under ₹X" into "trip under ₹X".
            decision.put(
                    TravelState.HOTEL_BUDGET,
                    modification.getHotelBudget());
        }

        /*
         * Optional conversation history.
         */
        if (!TravelState.isBlank(historyContext)) {

            decision.put(
                    TravelState.HISTORY_CONTEXT,
                    historyContext);
        }

        log.info(
                "Resuming graph for MODIFY type={} on same threadId={}",
                modification.getChangeType(),
                key);

        /*
         * This log is useful for verifying the fix.
         */
        log.info(
                "MODIFY USER REQUEST = [{}]",
                latestUserRequest);

        ModelRoutingContext.set(
                previous.modelPolicy());

        executionBudget.begin();

        graphRunContext.open(
                key,
                executionBudget.capture(),
                previous.modelPolicy());

        try {

            travelGraph.invoke(
                    GraphInput.resume(decision),
                    config);

        } finally {

            graphRunContext.close(key);
            executionBudget.end();
            ModelRoutingContext.clear();
        }

        TravelState state = requireCheckpointState(key);

        boolean pending = isAwaitingHitl(key);

        TravelPlanResponse response = toResponse(
                state,
                key,
                pending);
        tripHistoryService.saveOrUpdate(userId, response);
        return response;
    }

    public TravelPlanResponse reject(
            String userId,
            String threadId) {

        String key = requireOwnedThread(
                userId,
                threadId);

        requireCheckpointState(key);

        RunnableConfig config = configFor(key);

        Map<String, Object> decision = new LinkedHashMap<>();

        decision.put(
                TravelState.HITL_DECISION,
                "reject");

        decision.put(
                TravelState.AWAITING_APPROVAL,
                Boolean.FALSE);

        ModelRoutingContext.set(
                previousPolicy(key));

        executionBudget.begin();

        graphRunContext.open(
                key,
                executionBudget.capture(),
                previousPolicy(key));

        TravelState state;

        try {

            state = travelGraph.invoke(
                    GraphInput.resume(decision),
                    config)
                    .orElseGet(
                            () -> requireCheckpointState(key));

        } finally {

            graphRunContext.close(key);
            executionBudget.end();
            ModelRoutingContext.clear();
        }

        try {

            travelCheckpointSaver.release(config);

        } catch (Exception ex) {

            log.warn(
                    "Could not release checkpoint threadId={}",
                    key,
                    ex);
        }

        TravelPlanResponse response = toResponse(
                state,
                key,
                false);

        response.setStatus("REJECTED");
        response.setAwaitingApproval(false);
        response.getPlan().getTrip().setStatus("REJECTED");
        response.getPlan().getTrip().setAwaitingApproval(false);
        tripHistoryService.saveOrUpdate(userId, response);

        return response;
    }

    public Map<String, Object> history(
            String threadId) {

        String key = TravelState.firstNonBlank(threadId);

        StateSnapshot<TravelState> snapshot = travelGraph.getState(
                configFor(key));

        TravelState state = snapshot.state();

        Map<String, Object> body = new LinkedHashMap<>();

        body.put(
                "threadId",
                key);

        body.put(
                "next",
                snapshot.next());

        body.put(
                "pipeline",
                state.pipeline());

        body.put(
                "modelPolicy",
                state.modelPolicy());

        body.put(
                "intentConfidence",
                state.intentConfidence());

        body.put("ragDecision", state.ragDecision());
        body.put("ragQuery", state.ragQuery());
        body.put("ragSources", state.ragSources());
        body.put("ragIterations", state.ragIterations());
        body.put("ragSufficient", state.ragSufficient());
        body.put("ragRetrievalMethod", state.ragRetrievalMethod());
        body.put("ragCandidateCount", state.ragCandidateCount());
        body.put("ragRerankedCount", state.ragRerankedCount());
        body.put("ragContextChars", state.ragContextChars());
        body.put("ragEvidenceScore", state.ragEvidenceScore());
        body.put("ragGroundedness", state.ragGroundedness());
        body.put("ragJudgePass", state.ragJudgePass());
        body.put("ragJudgeReason", state.ragJudgeReason());

        body.put(
                "validationErrors",
                state.validationErrors());

        body.put(
                "semanticNotes",
                state.semanticNotes());

        if (state.planQuality() != null) {

            body.put(
                    "planQuality",
                    state.planQuality());
        }

        if (state.nodeFailure() != null
                && !TravelState.isBlank(
                        state.nodeFailure().getLastFailedNode())) {

            body.put(
                    "nodeFailure",
                    state.nodeFailure());
        }

        body.put(
                "semanticNotes",
                state.semanticNotes());

        body.put(
                "executionTimeline",
                state.pipeline());

        try {

            List<Map<String, Object>> snapshots = new ArrayList<>();

            for (StateSnapshot<TravelState> item : travelGraph.getStateHistory(
                    configFor(key))) {

                Map<String, Object> row = new LinkedHashMap<>();

                row.put(
                        "node",
                        item.node());

                row.put(
                        "next",
                        item.next());

                if (item.state() != null) {

                    row.put(
                            "pipeline",
                            item.state().pipeline());

                    row.put(
                            "supervisorDecision",
                            item.state().supervisorDecision());

                    row.put(
                            "dispatchRoute",
                            item.state().dispatchRoute());

                    if (item.state().planQuality() != null) {

                        row.put(
                                "planQuality",
                                item.state()
                                        .planQuality()
                                        .getOverall());
                    }
                }

                snapshots.add(row);
            }

            body.put(
                    "snapshots",
                    snapshots);

        } catch (Exception ex) {

            log.debug(
                    "Could not load state history for threadId={}",
                    key,
                    ex);

            body.put(
                    "snapshots",
                    List.of());
        }

        return body;
    }

    private String requireOwnedThread(
            String userId,
            String threadId) {

        String key = TravelState.firstNonBlank(
                threadId,
                userId);

        String requester = TravelState.firstNonBlank(
                userId,
                "anonymous");

        if (!key.equals(requester)
                && !key.startsWith(requester + "-")) {

            throw new IllegalArgumentException(
                    "Thread "
                            + key
                            + " is not owned by user "
                            + requester);
        }

        TravelState state = requireCheckpointState(key);

        String owner = TravelState.firstNonBlank(
                state.userId(),
                requester);

        if (!owner.equals(requester)) {

            throw new IllegalArgumentException(
                    "Thread "
                            + key
                            + " belongs to "
                            + owner
                            + ", not "
                            + requester);
        }

        return key;
    }

    private TravelState requireCheckpointState(
            String threadId) {

        try {

            StateSnapshot<TravelState> snapshot = travelGraph.getState(
                    configFor(threadId));

            if (snapshot == null
                    || snapshot.state() == null) {

                throw new IllegalStateException(
                        "No graph checkpoint for threadId="
                                + threadId
                                + ". Generate a plan first "
                                + "(or the thread expired).");
            }

            return snapshot.state();

        } catch (IllegalStateException ex) {

            throw ex;

        } catch (Exception ex) {

            throw new IllegalStateException(
                    "No graph checkpoint for threadId="
                            + threadId
                            + ". Generate a plan first "
                            + "(checkpoints survive restart "
                            + "via PostgreSQL).",
                    ex);
        }
    }

    private String previousPolicy(
            String threadId) {

        try {

            return requireCheckpointState(
                    threadId).modelPolicy();

        } catch (Exception ignored) {

            return "BALANCED";
        }
    }

    private boolean isAwaitingHitl(
            String threadId) {

        try {

            StateSnapshot<TravelState> snapshot = travelGraph.getState(
                    configFor(threadId));

            if (snapshot == null) {
                return false;
            }

            TravelState checkpoint = snapshot.state();
            if (checkpoint == null || !checkpoint.awaitingApproval()) {
                return false;
            }

            // The persisted state is the authoritative approval boundary.
            // LangGraph4j's snapshot.next() can vary around interruptBefore()
            // depending on the checkpoint implementation; using it as the
            // primary signal caused valid approval states to be reported as
            // COMPLETE. An explicit human decision always clears the gate.
            String decision = checkpoint.hitlDecision();
            return TravelState.isBlank(decision)
                    || "modify".equalsIgnoreCase(decision);

        } catch (Exception ex) {

            log.debug(
                    "Could not read HITL status for threadId={}",
                    threadId,
                    ex);

            return false;
        }
    }

    private void ensureOrigin(
            Map<String, Object> input) {

        if (!TravelState.isBlank(
                (String) input.get(
                        TravelState.ORIGIN))) {

            return;
        }

        String preferred = (String) input.get(
                TravelState.PREFERRED_AIRPORT);

        if (!TravelState.isBlank(preferred)) {

            input.put(
                    TravelState.ORIGIN,
                    preferred);

            return;
        }

        // Do not invent a departure city. Flight requests without an origin are
        // routed to the clarification gate instead of silently defaulting to BLR.
    }

    private void applyPreferences(
            Map<String, Object> input,
            UserPreference preference) {

        if (TravelState.isBlank(
                (String) input.get(
                        TravelState.ORIGIN))
                && preference.getPreferredAirport() != null) {

            input.put(
                    TravelState.ORIGIN,
                    preference.getPreferredAirport());

            input.put(
                    TravelState.PREFERRED_AIRPORT,
                    preference.getPreferredAirport());
        }

        if (preference.getTravelStyle() != null
                && TravelState.isBlank(
                        (String) input.get(
                                TravelState.TRAVEL_STYLE))) {

            input.put(
                    TravelState.TRAVEL_STYLE,
                    preference.getTravelStyle());
        }

        if (preference.getCurrency() != null) {

            input.put(
                    TravelState.CURRENCY,
                    preference.getCurrency());
        }

        String history = (String) input.getOrDefault(
                TravelState.HISTORY_CONTEXT,
                "");

        input.put(
                TravelState.HISTORY_CONTEXT,
                "Long-term preferences: airport="
                        + preference.getPreferredAirport()
                        + ", style="
                        + preference.getTravelStyle()
                        + ", currency="
                        + preference.getCurrency()
                        + ", lastDestination="
                        + preference.getLastDestination()
                        + "\n"
                        + history);
    }

    private RunnableConfig configFor(
            String threadId) {

        return RunnableConfig
                .builder(travelRunnableConfig)
                .threadId(threadId)
                .build();
    }

    private TravelPlanResponse toResponse(
            TravelState state,
            String threadId,
            boolean awaitingApproval) {

        return toResponse(
                state,
                threadId,
                awaitingApproval,
                "");
    }

    private TravelPlanResponse toResponse(
            TravelState state,
            String threadId,
            boolean awaitingApproval,
            String executionHistory) {

        String status = state.userInputRequired()
                ? "NEEDS_USER_INPUT"
                : (awaitingApproval ? "PENDING_APPROVAL" : "COMPLETE");

        // A HISTORY request is a read from persistent trip memory. Return the
        // exact saved structured plan instead of running Planner/specialists again.
        if ("HISTORY".equalsIgnoreCase(state.requestType()) && !TravelState.isBlank(state.historyResult())) {
            try {
                TravelPlanResponse saved = objectMapper.readValue(
                        state.historyResult(), TravelPlanResponse.class);
                saved.setThreadId(threadId);
                saved.setUserId(state.userId());
                saved.setRequestType("HISTORY");
                saved.setTripPlanning(false);
                saved.setAwaitingApproval(false);
                saved.setStatus("COMPLETE");
                if (saved.getPlan() != null && saved.getPlan().getTrip() != null) {
                    // A recalled plan is a read-only snapshot. Never expose the
                    // old HITL state or its approval controls on the new history
                    // conversation, otherwise the UI could send an approval to
                    // the wrong checkpoint/thread.
                    saved.getPlan().getTrip().setAwaitingApproval(false);
                    saved.getPlan().getTrip().setStatus("HISTORY");
                }
                return saved;
            } catch (Exception ex) {
                log.warn("Could not deserialize most recent saved trip for history request", ex);
            }
        }

        TripPlanResult plan = tripPlanAssembler.assemble(
                state,
                status,
                awaitingApproval);

        plan.setTips(
                TravelState.firstNonBlank(
                        state.finalTips(),
                        ""));

        AgentExecutionDetails execution = tripPlanAssembler.assembleExecution(
                state,
                executionHistory);

        TravelPlanResponse response = new TravelPlanResponse();

        response.setUserId(
                state.userId());

        response.setModel(
                TravelState.firstNonBlank(
                        state.modelPolicy(),
                        configuredModelsLabel()));

        response.setThreadId(
                threadId);

        response.setAwaitingApproval(
                awaitingApproval);
        response.setClarificationRequired(state.userInputRequired());
        response.setClarificationQuestion(state.userInputQuestion());

        response.setStatus(
                status);

        response.setRequestType(
                TravelState.firstNonBlank(state.requestType(), "GENERAL"));
        // Human approval is required only for an actual trip plan workflow.
        // Specialist lookups (weather, flights, hotels, research, budget) and
        // knowledge answers are informational and complete automatically.
        response.setTripPlanning(requiresTripPlanning(state));

        response.setPlan(
                plan);

        response.setExecution(
                execution);

        return response;
    }

    private boolean requiresTripPlanning(TravelState state) {
        return state != null && state.isTripPlanningWorkflow();
    }

    private String configuredModelsLabel() {

        return "planner="
                + travelModels.getPlanner()
                + "; extract="
                + travelModels.getExtraction()
                + "; itinerary="
                + travelModels.getItinerary()
                + "; final="
                + travelModels.getFinale();
    }
}