package com.example.travel.controller;

import com.example.travel.agent.TravelPlannerAgentService;
import com.example.travel.dto.PlanDecisionRequest;
import com.example.travel.dto.TravelPlanResponse;
import com.example.travel.dto.TravelRequest;
import com.example.travel.service.ConversationMemoryService;
import com.example.travel.service.GraphProgressHub;
import com.example.travel.service.QueryNormalizationService;
import com.example.travel.service.TripHistoryService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
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
    private final QueryNormalizationService queryNormalizationService;

    public TravelController(TravelPlannerAgentService travelPlannerAgentService,
                            ConversationMemoryService conversationMemoryService,
                            GraphProgressHub graphProgressHub,
                            TripHistoryService tripHistoryService,
                            QueryNormalizationService queryNormalizationService) {
        this.travelPlannerAgentService = travelPlannerAgentService;
        this.conversationMemoryService = conversationMemoryService;
        this.graphProgressHub = graphProgressHub;
        this.tripHistoryService = tripHistoryService;
        this.queryNormalizationService = queryNormalizationService;
    }

    @PostMapping("/plan")
    public ResponseEntity<TravelPlanResponse> createPlan(Authentication authentication,
                                                         @Valid @RequestBody TravelRequest request) {
        String userId = currentUser(authentication);
        request.setUserId(userId);
        String sessionId = userId;
        String query = request.getPrompt() != null ? request.getPrompt() : request.getPreferences();
        request.setOriginalPrompt(query);
        QueryNormalizationService.NormalizationResult normalization = queryNormalizationService.normalize(request);
        if (!normalization.normalizedPrompt().isBlank()) request.setPrompt(normalization.normalizedPrompt());
        log.info("Received travel plan request destination={} normalizedQuery={} corrections={}",
                request.getDestination(), normalization.normalizedPrompt(), normalization.corrections());

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
                    "Plan failed: " + safeError(ex));
            throw ex;
        }
    }

    @PostMapping("/plan/start")
    public ResponseEntity<Map<String, String>> startPlan(Authentication authentication,
                                                         @Valid @RequestBody TravelRequest request) {
        String userId = currentUser(authentication);
        request.setUserId(userId);
        String conversationId = request.getConversationId();
        if (conversationId == null || conversationId.isBlank()) {
            conversationId = userId + "-conversation";
            request.setConversationId(conversationId);
        }

        conversationMemoryService.hydrateRequestFromConversation(userId, conversationId, request);
        String rawPrompt = request.getPrompt();
        request.setOriginalPrompt(rawPrompt);
        QueryNormalizationService.NormalizationResult normalization = queryNormalizationService.normalize(request);
        if (!normalization.normalizedPrompt().isBlank()) request.setPrompt(normalization.normalizedPrompt());
        log.info("Query normalization conversationId={} normalizedQuery={} corrections={} entities={}",
                conversationId, normalization.normalizedPrompt(), normalization.corrections(), normalization.entities());

        String historyContext = conversationMemoryService.buildConversationHistoryContext(userId, conversationId);
        String threadId = travelPlannerAgentService.startTravelPlan(request, historyContext);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of("threadId", threadId, "status", "STARTED"));
    }

    @GetMapping(value = "/plan/{threadId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter planEvents(Authentication authentication, @PathVariable String threadId) {
        String userId = currentUser(authentication);
        travelPlannerAgentService.assertOwnedThread(userId, threadId);
        return graphProgressHub.subscribe(threadId);
    }

    @GetMapping("/chat/history")
    public ResponseEntity<?> chatHistory(Authentication authentication,
                                         @RequestParam(defaultValue = "40") int limit) {
        String userId = currentUser(authentication);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(conversationMemoryService.getHistoryForUi(userId, limit));
    }

    @PostMapping("/plan/approve")
    public ResponseEntity<TravelPlanResponse> approve(Authentication authentication,
                                                      @RequestBody PlanDecisionRequest request) {
        String userId = currentUser(authentication);
        request.setUserId(userId);
        requireThread(request.getThreadId());
        TravelPlanResponse response = travelPlannerAgentService.approve(userId, request.getThreadId());
        conversationMemoryService.saveUiMessage(userId, request.getThreadId(), "assistant", responseMessage(response, "Plan approved"), response);
        tripHistoryService.saveOrUpdate(userId, response);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/plan/modify")
    public ResponseEntity<TravelPlanResponse> modify(Authentication authentication,
                                                      @RequestBody PlanDecisionRequest request) {
        String userId = currentUser(authentication);
        request.setUserId(userId);
        requireThread(request.getThreadId());
        conversationMemoryService.saveMessage(userId, request.getThreadId(), "user",
                "Modify: " + (request.getNotes() == null ? "(no details)" : request.getNotes()));
        String historyContext = conversationMemoryService.buildHistoryContext(userId, request.getThreadId());
        try {
            TravelPlanResponse response = travelPlannerAgentService.modify(userId, request.getThreadId(),
                    request.getNotes() == null ? "Please adjust the plan" : request.getNotes(), historyContext);
            conversationMemoryService.saveUiMessage(userId, request.getThreadId(), "assistant",
                    responseMessage(response, "Modified plan ready"), response);
            tripHistoryService.saveOrUpdate(userId, response);
            return ResponseEntity.ok(response);
        } catch (RuntimeException ex) {
            conversationMemoryService.saveMessage(userId, request.getThreadId(), "assistant", "Modify failed: " + safeError(ex));
            throw ex;
        }
    }

    @PostMapping("/plan/reject")
    public ResponseEntity<TravelPlanResponse> reject(Authentication authentication,
                                                      @RequestBody PlanDecisionRequest request) {
        String userId = currentUser(authentication);
        request.setUserId(userId);
        requireThread(request.getThreadId());
        TravelPlanResponse response = travelPlannerAgentService.reject(userId, request.getThreadId());
        conversationMemoryService.saveUiMessage(userId, request.getThreadId(), "assistant", responseMessage(response, "Plan rejected"), response);
        tripHistoryService.saveOrUpdate(userId, response);
        return ResponseEntity.ok(response);
    }

    private String responseMessage(TravelPlanResponse response, String fallback) {
        if (response == null || response.getPlan() == null) return fallback;
        String tips = response.getPlan().getTips();
        if (tips != null && !tips.isBlank()) return tips;
        if (response.getPlan().getItinerary() != null && !response.getPlan().getItinerary().isEmpty()) {
            return response.getPlan().getItinerary().toDisplay();
        }
        return fallback;
    }

    @GetMapping("/trips")
    public ResponseEntity<?> trips(Authentication authentication, @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(tripHistoryService.list(currentUser(authentication), limit));
    }

    @GetMapping("/trips/{id}")
    public ResponseEntity<?> trip(Authentication authentication, @PathVariable Long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(tripHistoryService.getPlan(currentUser(authentication), id));
    }

    @DeleteMapping("/history")
    public ResponseEntity<?> deleteHistory(Authentication authentication,
                                           @RequestParam(required = false) String sessionId,
                                           @RequestParam(required = false) String conversationId,
                                           @RequestParam(required = false) Long tripId) {
        String owner = currentUser(authentication);
        if (tripId != null) {
            boolean deleted = tripHistoryService.deleteTripAndMemory(owner, tripId);
            if (!deleted) return ResponseEntity.notFound().build();
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
    public ResponseEntity<?> chatSession(Authentication authentication, @PathVariable String sessionId,
                                         @RequestParam(defaultValue = "100") int limit) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(conversationMemoryService.getHistoryForSessionUi(currentUser(authentication), sessionId, limit));
    }

    @GetMapping("/chat/conversation/{conversationId}")
    public ResponseEntity<?> chatConversation(Authentication authentication, @PathVariable String conversationId,
                                              @RequestParam(defaultValue = "200") int limit) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(conversationMemoryService.getHistoryForConversationUi(currentUser(authentication), conversationId, limit));
    }

    @GetMapping("/chat/message/{id}")
    public ResponseEntity<?> chatMessage(Authentication authentication, @PathVariable Long id) {
        return conversationMemoryService.getMessageForUi(currentUser(authentication), id)
                .map(message -> ResponseEntity.ok(message))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/plan/{threadId}/restore")
    public ResponseEntity<?> restorePlan(Authentication authentication, @PathVariable String threadId) {
        return ResponseEntity.ok(travelPlannerAgentService.restore(currentUser(authentication), threadId));
    }

    @GetMapping("/plan/{threadId}/history")
    public ResponseEntity<?> history(Authentication authentication, @PathVariable String threadId) {
        return ResponseEntity.ok(travelPlannerAgentService.history(currentUser(authentication), threadId));
    }

    private String currentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new IllegalStateException("Authenticated user is required");
        }
        return authentication.getName();
    }

    private void requireThread(String threadId) {
        if (threadId == null || threadId.isBlank()) throw new IllegalArgumentException("Thread id is required");
    }

    private String safeError(RuntimeException ex) {
        return ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
    }
}
