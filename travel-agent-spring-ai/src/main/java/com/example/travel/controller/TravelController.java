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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
        log.info("Received travel plan request for destination={}, query={}, userId={}",
                request.getDestination(), request.getPrompt() != null ? request.getPrompt() : request.getPreferences(), userId);
        String historyContext = conversationMemoryService.buildHistoryContext(userId, sessionId);
        TravelPlanResponse response = travelPlannerAgentService.createTravelPlan(request, historyContext);
        conversationMemoryService.saveMessage(userId, sessionId, "user",
                request.getPrompt() != null ? request.getPrompt() : request.getPreferences());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/plan/approve")
    public ResponseEntity<TravelPlanResponse> approve(@RequestBody PlanDecisionRequest request) {
        String userId = request.getUserId() != null ? request.getUserId() : "anonymous";
        TravelPlanResponse response = travelPlannerAgentService.approve(userId, request.getThreadId() != null ? request.getThreadId() : userId);
        conversationMemoryService.saveMessage(userId, userId, "assistant", response.getFinalPlan());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/plan/modify")
    public ResponseEntity<TravelPlanResponse> modify(@RequestBody PlanDecisionRequest request) {
        String userId = request.getUserId() != null ? request.getUserId() : "anonymous";
        String historyContext = conversationMemoryService.buildHistoryContext(userId, userId);
        TravelPlanResponse response = travelPlannerAgentService.modify(userId,
                request.getThreadId() != null ? request.getThreadId() : userId,
                request.getNotes() == null ? "Please make it cheaper" : request.getNotes(),
                historyContext);
        conversationMemoryService.saveMessage(userId, userId, "user", "Modify: " + request.getNotes());
        return ResponseEntity.ok(response);
    }
}
