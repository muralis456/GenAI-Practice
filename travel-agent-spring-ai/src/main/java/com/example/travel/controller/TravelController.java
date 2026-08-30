package com.example.travel.controller;

import com.example.travel.dto.TravelPlanResponse;
import com.example.travel.dto.TravelRequest;
import com.example.travel.service.ConversationMemoryService;
import com.example.travel.service.TravelPlannerOrchestratorAgentService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class TravelController {

    private static final Logger log = LoggerFactory.getLogger(TravelController.class);

    private final TravelPlannerOrchestratorAgentService travelPlannerOrchestratorAgentService;
    private final ConversationMemoryService conversationMemoryService;

    public TravelController(TravelPlannerOrchestratorAgentService travelPlannerOrchestratorAgentService,
                            ConversationMemoryService conversationMemoryService) {
        this.travelPlannerOrchestratorAgentService = travelPlannerOrchestratorAgentService;
        this.conversationMemoryService = conversationMemoryService;
    }

    @PostMapping("/plan")
    public ResponseEntity<TravelPlanResponse> createPlan(@Valid @RequestBody TravelRequest request) {
        String userId = request.getUserId() != null ? request.getUserId() : "anonymous";
        String sessionId = userId;

        log.info("Received travel plan request for destination={}, userId={}", request.getDestination(), userId);

        List<com.example.travel.entity.ConversationMemory> history = conversationMemoryService.getRecentHistory(userId, sessionId);
        String historyContext = buildHistoryContext(history);

        TravelPlanResponse response = travelPlannerOrchestratorAgentService.createTravelPlan(request, historyContext);

        conversationMemoryService.saveMessage(userId, sessionId, "user", request.getPrompt() != null ? request.getPrompt() : request.getPreferences());
        conversationMemoryService.saveMessage(userId, sessionId, "assistant", response.getFinalPlan());

        log.info("Travel plan generated successfully for userId={}", userId);
        return ResponseEntity.ok(response);
    }

    private String buildHistoryContext(List<com.example.travel.entity.ConversationMemory> history) {
        if (history == null || history.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("Previous conversation context:\n");
        for (com.example.travel.entity.ConversationMemory memory : history) {
            sb.append(memory.getRole()).append(": ").append(memory.getContent()).append("\n");
        }
        return sb.toString();
    }
}
