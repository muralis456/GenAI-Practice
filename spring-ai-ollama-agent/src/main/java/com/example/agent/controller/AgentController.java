package com.example.agent.controller;

import com.example.agent.service.OllamaAgentService;
import jakarta.servlet.http.HttpSession;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class AgentController {
    private static final Logger log = LoggerFactory.getLogger(AgentController.class);

    private final OllamaAgentService agentService;

    public AgentController(OllamaAgentService agentService) {
        this.agentService = agentService;
    }

    @GetMapping("/")
    public String home() {
        return "index";
    }

    @GetMapping("/ask")
    @ResponseBody
    public ResponseEntity<String> askGet(
            @RequestParam(defaultValue = "Hello, what can you do?") String prompt,
            @RequestParam(defaultValue = "llama3.2:3b") String model,
            HttpSession session) {
        log.info("GET /ask received prompt for model {}: {}", model, prompt);
        return ResponseEntity.ok(agentService.ask(prompt, model, getConversationHistory(session)));
    }

    @PostMapping("/ask")
    @ResponseBody
    public ResponseEntity<String> askPost(
            @RequestParam String prompt,
            @RequestParam(defaultValue = "llama3.2:3b") String model,
            HttpSession session) {
        log.info("POST /ask received prompt for model {}: {}", model, prompt);

        List<Message> previousMessages = getConversationHistory(session);
        String response = agentService.ask(prompt, model, previousMessages);
        previousMessages.add(new org.springframework.ai.chat.messages.UserMessage(prompt));
        previousMessages.add(new org.springframework.ai.chat.messages.AssistantMessage(response));
        session.setAttribute("conversationHistory", previousMessages);

        return ResponseEntity.ok(response);
    }

    @SuppressWarnings("unchecked")
    private List<Message> getConversationHistory(HttpSession session) {
        Object attr = session.getAttribute("conversationHistory");
        if (attr instanceof List<?>) {
            return new ArrayList<>((List<Message>) attr);
        }
        return new ArrayList<>();
    }
}
