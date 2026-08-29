package com.example.agent.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.stereotype.Service;

@Service
public class OllamaAgentService {
    private static final Logger log = LoggerFactory.getLogger(OllamaAgentService.class);
    private static final String DEFAULT_MODEL = "llama3.2:3b";
    private static final Set<String> LOCAL_MODELS = Set.of(
            "llama3.2:3b",
            "devstral:24b",
            "qwen3-coder:30b"
    );

    private final ChatClient chatClient;

    public OllamaAgentService(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public String ask(String prompt) {
        return ask(prompt, DEFAULT_MODEL, List.of());
    }

    public String ask(String prompt, String selectedModel) {
        return ask(prompt, selectedModel, List.of());
    }

    public String ask(String prompt, String selectedModel, List<Message> previousMessages) {
        String model = normalizeModel(selectedModel);
        log.info("Received prompt for Ollama agent using model {}: {}", model, prompt);

        try {
            List<Message> messages = previousMessages == null ? new ArrayList<>() : new ArrayList<>(previousMessages);
            messages.add(new UserMessage(prompt));

            String response = chatClient.prompt()
                    .options(OllamaChatOptions.builder().model(model))
                    .messages(messages.toArray(new Message[0]))
                    .call()
                    .content();

            messages.add(new AssistantMessage(response));
            log.info("Ollama response received successfully for model {} using prompt: {}", model, prompt);
            return response;
        } catch (Exception e) {
            log.error("Failed to process prompt with Ollama using model {}: {}", model, prompt, e);
            throw new IllegalStateException("Unable to process the AI request at the moment.", e);
        }
    }

    private String normalizeModel(String model) {
        if (model == null) {
            return DEFAULT_MODEL;
        }

        String trimmed = model.trim();
        return LOCAL_MODELS.contains(trimmed) ? trimmed : DEFAULT_MODEL;
    }
}
