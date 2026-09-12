package com.example.travel.service;

import com.example.travel.config.TravelModelsProperties;
import com.example.travel.config.TravelModelsProperties.AgentRole;
import com.example.travel.model.LlmExecutionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.stream.Collectors;

@Component
public class RoutedLlm {

    private static final Logger log = LoggerFactory.getLogger(RoutedLlm.class);

    private final ChatClient chatClient;
    private final TravelModelsProperties models;
    private final AgentExecutionBudget executionBudget;

    public RoutedLlm(ChatClient chatClient,
                     TravelModelsProperties models,
                     AgentExecutionBudget executionBudget) {
        this.chatClient = chatClient;
        this.models = models;
        this.executionBudget = executionBudget;
    }

    public String complete(AgentRole role, String system, String user) {
        return complete(role, system, user, (Object[]) null);
    }

    public String complete(AgentRole role, String system, String user, Object... tools) {
        return completeWithMeta(role, system, user, tools).content();
    }

    public LlmExecutionResult completeWithMeta(AgentRole role, String system, String user, Object... tools) {
        if (!executionBudget.tryConsumeLlm()) {
            return new LlmExecutionResult("LLM budget exhausted for this graph run.", "", "", 0);
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
                .temperature(models.temperature(role)))
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

    private static int safeTokens(Integer value) {
        return value == null ? 0 : value;
    }
}
