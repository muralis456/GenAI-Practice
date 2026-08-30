package com.example.travel.controller;

import com.example.travel.dto.TravelPlanResponse;
import com.example.travel.dto.TravelRequest;
import com.example.travel.service.TravelPlannerOrchestratorAgentService;
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

    private final TravelPlannerOrchestratorAgentService travelPlannerOrchestratorAgentService;

    public TravelController(TravelPlannerOrchestratorAgentService travelPlannerOrchestratorAgentService) {
        this.travelPlannerOrchestratorAgentService = travelPlannerOrchestratorAgentService;
    }

    @PostMapping("/plan")
    public ResponseEntity<TravelPlanResponse> createPlan(@Valid @RequestBody TravelRequest request) {
        log.info("Received travel plan request for destination={}, userId={}", request.getDestination(), request.getUserId());
        TravelPlanResponse response = travelPlannerOrchestratorAgentService.createTravelPlan(request);
        log.info("Travel plan generated successfully for userId={}", request.getUserId());
        return ResponseEntity.ok(response);
    }
}
