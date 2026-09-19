package com.example.travel.controller;

import com.example.travel.dto.PlanDecisionRequest;
import com.example.travel.dto.TravelPlanResponse;
import com.example.travel.dto.TravelRequest;
import com.example.travel.agent.TravelPlannerAgentService;
import com.example.travel.service.ConversationMemoryService;
import com.example.travel.service.GraphProgressHub;
import com.example.travel.service.TripHistoryService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.CacheControl;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class TravelController {

    private static final Logger log = LoggerFactory.getLogger(TravelController.class);

    private final TravelPlannerAgentService travelPlannerAgentService;
    private final ConversationMemoryService conversationMemoryService;
    private final GraphProgressHub graphProgressHub;
    private final TripHistoryService tripHistoryService;

    public TravelController(TravelPlannerAgentService travelPlannerAgentService,
                            ConversationMemoryService conversationMemoryService,
                            GraphProgressHub graphProgressHub,
                            TripHistoryService tripHistoryService) {
        this.travelPlannerAgentService = travelPlannerAgentService;
        this.conversationMemoryService = conversationMemoryService;
        this.graphProgressHub = graphProgressHub;
        this.tripHistoryService = tripHistoryService;
    }

    @PostMapping("/plan")
    public ResponseEntity<TravelPlanResponse> createPlan(@Valid @RequestBody TravelRequest request) {
        String userId = request.getUserId() != null ? request.getUserId() : "anonymous";
        String sessionId = userId;
        String query = request.getPrompt() != null ? request.getPrompt() : request.getPreferences();
        log.info("Received travel plan request for destination={}, query={}, userId={}",
                request.getDestination(), query, userId);

        // Persist the search immediately — do not wait for the multi-minute graph.
        conversationMemoryService.saveMessage(userId, sessionId, "user", query);

        String historyContext = conversationMemoryService.buildHistoryContext(userId, sessionId);
        try {
            TravelPlanResponse response = travelPlannerAgentService.createTravelPlan(request, historyContext);
            conversationMemoryService.saveUiMessage(userId, response.getThreadId(), "assistant",
                    responseMessage(response, "Plan ready"), response);
            tripHistoryService.saveOrUpdate(userId, response);
            return ResponseEntity.ok(response);
        } catch (RuntimeException ex) {
            conversationMemoryService.saveMessage(userId, sessionId, "assistant",
                    "Plan failed: " + (ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()));
            throw ex;
        }
    }

    @PostMapping("/plan/start")
    public ResponseEntity<Map<String, String>> startPlan(@Valid @RequestBody TravelRequest request) {
        String userId = request.getUserId() != null ? request.getUserId() : "anonymous";
        String conversationId = request.getConversationId();
        if (conversationId == null || conversationId.isBlank()) {
            // Backward-compatible fallback for API clients that have not adopted
            // conversationId yet. New browser clients always send a stable id.
            conversationId = userId;
            request.setConversationId(conversationId);
        }

        // IMPORTANT: history is scoped to the stable conversation, not to the
        // new LangGraph thread. Every follow-up can therefore create a fresh
        // execution thread while still seeing all previous turns.
        // Hydrate missing route slots first so specialists work even for API
        // clients that do not perform browser-side follow-up context merging.
        conversationMemoryService.hydrateRequestFromConversation(userId, conversationId, request);
        String historyContext = conversationMemoryService.buildConversationHistoryContext(userId, conversationId);
        String threadId = travelPlannerAgentService.startTravelPlan(request, historyContext);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                "threadId", threadId,
                "status", "STARTED"));
    }

    @GetMapping(value = "/plan/{threadId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter planEvents(@PathVariable String threadId) {
        return graphProgressHub.subscribe(threadId);
    }

    @GetMapping("/chat/history")
    public ResponseEntity<?> chatHistory(@RequestParam(defaultValue = "anonymous") String userId,
                                         @RequestParam(defaultValue = "40") int limit) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(conversationMemoryService.getHistoryForUi(userId, limit));
    }

    @PostMapping("/plan/approve")
    public ResponseEntity<TravelPlanResponse> approve(@RequestBody PlanDecisionRequest request) {
        String userId = request.getUserId() != null ? request.getUserId() : "anonymous";
        if (request.getThreadId() == null || request.getThreadId().isBlank()) {
            return ResponseEntity.badRequest().body(null);
        }
        TravelPlanResponse response = travelPlannerAgentService.approve(userId, request.getThreadId());
        conversationMemoryService.saveUiMessage(userId, request.getThreadId(), "assistant", responseMessage(response, "Plan approved"), response);
        tripHistoryService.saveOrUpdate(userId, response);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/plan/modify")
    public ResponseEntity<TravelPlanResponse> modify(@RequestBody PlanDecisionRequest request) {
        String userId = request.getUserId() != null ? request.getUserId() : "anonymous";
        if (request.getThreadId() == null || request.getThreadId().isBlank()) {
            return ResponseEntity.badRequest().body(null);
        }
        conversationMemoryService.saveMessage(userId, request.getThreadId(), "user",
                "Modify: " + (request.getNotes() == null ? "(no details)" : request.getNotes()));
        String historyContext = conversationMemoryService.buildHistoryContext(userId, request.getThreadId());
        try {
            TravelPlanResponse response = travelPlannerAgentService.modify(userId,
                    request.getThreadId(),
                    request.getNotes() == null ? "Please adjust the plan" : request.getNotes(),
                    historyContext);
            conversationMemoryService.saveUiMessage(userId, request.getThreadId(), "assistant",
                    responseMessage(response, "Modified plan ready"), response);
            tripHistoryService.saveOrUpdate(userId, response);
            return ResponseEntity.ok(response);
        } catch (RuntimeException ex) {
            conversationMemoryService.saveMessage(userId, request.getThreadId(), "assistant",
                    "Modify failed: " + (ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()));
            throw ex;
        }
    }

    @PostMapping("/plan/reject")
    public ResponseEntity<TravelPlanResponse> reject(@RequestBody PlanDecisionRequest request) {
        String userId = request.getUserId() != null ? request.getUserId() : "anonymous";
        if (request.getThreadId() == null || request.getThreadId().isBlank()) {
            return ResponseEntity.badRequest().body(null);
        }
        TravelPlanResponse response = travelPlannerAgentService.reject(userId, request.getThreadId());
        conversationMemoryService.saveUiMessage(userId, request.getThreadId(), "assistant",
                responseMessage(response, "Plan rejected"), response);
        tripHistoryService.saveOrUpdate(userId, response);
        return ResponseEntity.ok(response);
    }

    private String responseMessage(TravelPlanResponse response, String fallback) {
        if (response == null || response.getPlan() == null) {
            return fallback;
        }
        String tips = response.getPlan().getTips();
        if (tips != null && !tips.isBlank()) {
            return tips;
        }
        if (response.getPlan().getItinerary() != null
                && !response.getPlan().getItinerary().isEmpty()) {
            return response.getPlan().getItinerary().toDisplay();
        }
        return fallback;
    }

    @GetMapping("/trips")
    public ResponseEntity<?> trips(@RequestParam(defaultValue = "anonymous") String userId,
                                   @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(tripHistoryService.list(userId, limit));
    }

    @GetMapping("/trips/{id}")
    public ResponseEntity<?> trip(@PathVariable Long id,
                                  @RequestParam(defaultValue = "anonymous") String userId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(tripHistoryService.getPlan(userId, id));
    }

    @DeleteMapping("/history")
    public ResponseEntity<?> deleteHistory(
            @RequestParam(defaultValue = "anonymous") String userId,
            @RequestParam(required = false) String sessionId,
            @RequestParam(required = false) String conversationId,
            @RequestParam(required = false) Long tripId) {
        String owner = userId == null || userId.isBlank() ? "anonymous" : userId;

        if (tripId != null) {
            boolean deleted = tripHistoryService.deleteTripAndMemory(owner, tripId);
            if (!deleted) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(Map.of("deleted", true, "type", "trip"));
        }

        if (conversationId != null && !conversationId.isBlank()) {
            long deletedRows = conversationMemoryService.deleteConversation(owner, conversationId);
            return ResponseEntity.ok(Map.of("deleted", true, "type", "conversation", "memoryRows", deletedRows));
        }
        if (sessionId == null || sessionId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "History session is required"));
        }
        long deletedRows = conversationMemoryService.deleteSession(owner, sessionId);
        return ResponseEntity.ok(Map.of("deleted", true, "type", "conversation", "memoryRows", deletedRows));
    }

    @GetMapping("/chat/session/{sessionId}")
    public ResponseEntity<?> chatSession(@PathVariable String sessionId,
                                         @RequestParam(defaultValue = "anonymous") String userId,
                                         @RequestParam(defaultValue = "100") int limit) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(conversationMemoryService.getHistoryForSessionUi(userId, sessionId, limit));
    }

    @GetMapping("/chat/conversation/{conversationId}")
    public ResponseEntity<?> chatConversation(@PathVariable String conversationId,
                                              @RequestParam(defaultValue = "anonymous") String userId,
                                              @RequestParam(defaultValue = "200") int limit) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(conversationMemoryService.getHistoryForConversationUi(userId, conversationId, limit));
    }

    @GetMapping("/chat/message/{id}")
    public ResponseEntity<?> chatMessage(@PathVariable Long id,
                                         @RequestParam(defaultValue = "anonymous") String userId) {
        return conversationMemoryService.getMessageForUi(userId, id)
                .map(message -> ResponseEntity.ok(message))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/plan/{threadId}/restore")
    public ResponseEntity<?> restorePlan(@PathVariable String threadId,
                                         @RequestParam(defaultValue = "anonymous") String userId) {
        return ResponseEntity.ok(travelPlannerAgentService.restore(userId, threadId));
    }

    @GetMapping("/plan/{threadId}/history")
    public ResponseEntity<?> history(@PathVariable String threadId) {
        return ResponseEntity.ok(travelPlannerAgentService.history(threadId));
    }
}
