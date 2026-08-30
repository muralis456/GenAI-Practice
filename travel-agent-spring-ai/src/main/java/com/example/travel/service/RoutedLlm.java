package com.example.travel.service;

import com.example.travel.config.TravelModelsProperties;
import com.example.travel.config.TravelModelsProperties.AgentRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.stereotype.Component;

@Component
public class RoutedLlm {

    private static final Logger log = LoggerFactory.getLogger(RoutedLlm.class);

    private final ChatClient chatClient;
    private final TravelModelsProperties models;

    public RoutedLlm(ChatClient chatClient, TravelModelsProperties models) {
        this.chatClient = chatClient;
        this.models = models;
    }

    public String complete(AgentRole role, String system, String user) {
        return complete(role, system, user, (Object[]) null);
    }

    public String complete(AgentRole role, String system, String user, Object... tools) {
        String model = models.model(role);
        log.info("Routing {} to Ollama model={} tools={}", role, model, tools == null ? 0 : tools.length);
        var prompt = chatClient.prompt()
                .options(OllamaChatOptions.builder()
                        .model(model)
                        .temperature(models.temperature(role)))
                .system(system)
                .user(user);
        if (tools != null && tools.length > 0) {
            prompt = prompt.tools(tools);
        }
        return prompt.call().content();
    }
}
