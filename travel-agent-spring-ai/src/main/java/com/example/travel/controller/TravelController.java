package com.example.travel.controller;

import com.example.travel.dto.PlanDecisionRequest;
import com.example.travel.dto.TravelPlanResponse;
import com.example.travel.dto.TravelRequest;
import com.example.travel.service.ConversationMemoryService;
import com.example.travel.service.TravelPlannerAgentService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class TravelController {

    private static final Logger log = LoggerFactory.getLogger(TravelController.class);

    private final TravelPlannerAgentService travelPlannerAgentService;
    private final ConversationMemoryService conversationMemoryService;

    public TravelController(TravelPlannerAgentService travelPlannerAgentService,
                            ConversationMemoryService conversationMemoryService) {
        this.travelPlannerAgentService = travelPlannerAgentService;
        this.conversationMemoryService = conversationMemoryService;
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
            conversationMemoryService.saveUiMessage(userId, sessionId, "assistant",
                    response.getFinalPlan() != null ? response.getFinalPlan()
                            : (response.getRouteSummary() != null ? response.getRouteSummary() : "Plan ready"));
            return ResponseEntity.ok(response);
        } catch (RuntimeException ex) {
            conversationMemoryService.saveMessage(userId, sessionId, "assistant",
                    "Plan failed: " + (ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()));
            throw ex;
        }
    }

    @GetMapping("/chat/history")
    public ResponseEntity<?> chatHistory(@RequestParam(defaultValue = "anonymous") String userId,
                                         @RequestParam(defaultValue = "40") int limit) {
        return ResponseEntity.ok(conversationMemoryService.getHistoryForUi(userId, limit));
    }

    @PostMapping("/plan/approve")
    public ResponseEntity<TravelPlanResponse> approve(@RequestBody PlanDecisionRequest request) {
        String userId = request.getUserId() != null ? request.getUserId() : "anonymous";
        TravelPlanResponse response = travelPlannerAgentService.approve(userId, request.getThreadId() != null ? request.getThreadId() : userId);
        conversationMemoryService.saveUiMessage(userId, userId, "assistant", response.getFinalPlan());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/plan/modify")
    public ResponseEntity<TravelPlanResponse> modify(@RequestBody PlanDecisionRequest request) {
        String userId = request.getUserId() != null ? request.getUserId() : "anonymous";
        conversationMemoryService.saveMessage(userId, userId, "user",
                "Modify: " + (request.getNotes() == null ? "Please make it cheaper" : request.getNotes()));
        String historyContext = conversationMemoryService.buildHistoryContext(userId, userId);
        try {
            TravelPlanResponse response = travelPlannerAgentService.modify(userId,
                    request.getThreadId() != null ? request.getThreadId() : userId,
                    request.getNotes() == null ? "Please make it cheaper" : request.getNotes(),
                    historyContext);
            conversationMemoryService.saveUiMessage(userId, userId, "assistant",
                    response.getFinalPlan() != null ? response.getFinalPlan() : "Modified plan ready");
            return ResponseEntity.ok(response);
        } catch (RuntimeException ex) {
            conversationMemoryService.saveMessage(userId, userId, "assistant",
                    "Modify failed: " + (ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()));
            throw ex;
        }
    }
}
