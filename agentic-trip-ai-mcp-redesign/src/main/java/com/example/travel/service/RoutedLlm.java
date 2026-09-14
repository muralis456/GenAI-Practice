package com.example.travel.service;

import com.example.travel.config.TravelModelsProperties;
import com.example.travel.config.TravelModelsProperties.AgentRole;
import com.example.travel.model.LlmExecutionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.stream.Collectors;

@Component
public class RoutedLlm {

    private static final Logger log = LoggerFactory.getLogger(RoutedLlm.class);

    private final ChatClient chatClient;
    private final TravelModelsProperties models;
    private final AgentExecutionBudget executionBudget;
    private final int extractionMaxTokens;
    private final int plannerMaxTokens;
    private final int itineraryMaxTokens;
    private final int finalMaxTokens;

    public RoutedLlm(ChatClient chatClient,
                     TravelModelsProperties models,
                     AgentExecutionBudget executionBudget,
                     @Value("${travel.models.max-tokens.extraction:384}") int extractionMaxTokens,
                     @Value("${travel.models.max-tokens.planner:512}") int plannerMaxTokens,
                     @Value("${travel.models.max-tokens.itinerary:900}") int itineraryMaxTokens,
                     @Value("${travel.models.max-tokens.final:700}") int finalMaxTokens) {
        this.chatClient = chatClient;
        this.models = models;
        this.executionBudget = executionBudget;
        this.extractionMaxTokens = Math.max(128, extractionMaxTokens);
        this.plannerMaxTokens = Math.max(128, plannerMaxTokens);
        this.itineraryMaxTokens = Math.max(256, itineraryMaxTokens);
        this.finalMaxTokens = Math.max(256, finalMaxTokens);
    }

    public String complete(AgentRole role, String system, String user) {
        return complete(role, system, user, (Object[]) null);
    }

    public String complete(AgentRole role, String system, String user, Object... tools) {
        return completeWithMeta(role, system, user, tools).content();
    }

    public LlmExecutionResult completeWithMeta(AgentRole role, String system, String user, Object... tools) {
        if (!executionBudget.tryConsumeLlm()) {
            log.warn("LLM execution budget exhausted; returning an empty model result so internal state is never exposed to the user");
            return new LlmExecutionResult("", "", "", 0);
        }
        String policy = ModelRoutingContext.get();
        ModelRoutingContext.Complexity complexity = ModelRoutingContext.getComplexity();
        String model = models.resolve(role, policy);
        String toolNames = tools == null || tools.length == 0 ? ""
                : Arrays.stream(tools).map(tool -> tool.getClass().getSimpleName()).collect(Collectors.joining(","));
        log.info("Routing {} to Ollama model={} policy={} complexity={} tools={}",
                role, model, policy, complexity, toolNames);
        long started = System.currentTimeMillis();
        var prompt = chatClient.prompt()
            .options(OllamaChatOptions.builder()
                .model(model)
                .temperature(models.temperature(role))
                .numPredict(maxTokens(role)))
            .system(system)
            .user(user);
        if (tools != null && tools.length > 0) {
            prompt = prompt.tools(tools);
        }
        var call = prompt.call();
        String content = call.content();
        int inputTokens = 0;
        int outputTokens = 0;
        try {
            var response = call.chatResponse();
            if (response != null && response.getMetadata() != null && response.getMetadata().getUsage() != null) {
                var usage = response.getMetadata().getUsage();
                inputTokens = safeTokens(usage.getPromptTokens());
                outputTokens = safeTokens(usage.getCompletionTokens());
            }
        } catch (Exception ignored) {
            // Some Ollama responses omit usage.
        }
        LlmExecutionResult result = new LlmExecutionResult(content, model, toolNames,
                System.currentTimeMillis() - started, inputTokens, outputTokens);
        LlmCallContext.record(result);
        return result;
    }

    private int maxTokens(AgentRole role) {
        return switch (role) {
            case PLANNER -> plannerMaxTokens;
            case ITINERARY -> itineraryMaxTokens;
            case FINAL -> finalMaxTokens;
            case EXTRACT -> extractionMaxTokens;
        };
    }

    private static int safeTokens(Integer value) {
        return value == null ? 0 : value;
    }
}
