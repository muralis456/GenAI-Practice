package com.example.travel.service;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@ConditionalOnProperty(prefix = "travel.mcp.client", name = "enabled", havingValue = "true")
public class McpToolClient {

    private static final Logger log = LoggerFactory.getLogger(McpToolClient.class);

    private final ToolCallbackProvider toolCallbackProvider;
    private final ObjectMapper objectMapper;
    private final Set<String> allowedTools;
    private final int maxAttempts;

    public McpToolClient(ToolCallbackProvider toolCallbackProvider,
                         ObjectMapper objectMapper,
                         @Value("${TRAVEL_MCP_CLIENT_ALLOWED_TOOLS:}") String allowedTools,
                         @Value("${travel.mcp.client.max-attempts:2}") int maxAttempts) {
        this.toolCallbackProvider = toolCallbackProvider;
        this.objectMapper = objectMapper;
        this.allowedTools = allowedTools == null || allowedTools.isBlank()
            ? Set.of()
            : Arrays.stream(allowedTools.split(","))
                .map(String::trim)
                .filter(tool -> !tool.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
        this.maxAttempts = Math.max(1, maxAttempts);
    }

    public JsonNode call(String toolName, Map<String, Object> arguments) throws Exception {
        if (!allowedTools.isEmpty() && !allowedTools.contains(toolName)) {
            throw new IllegalStateException("MCP tool is not allowed: " + toolName);
        }
        ToolCallback callback = find(toolName);
        Exception last = null;
        long started = System.nanoTime();
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                JsonNode result = responseTree(callback.call(objectMapper.writeValueAsString(arguments)));
                if (result.has("success")) {
                    log.info("mcp.client.response tool={} success={} errorCode={} message={}",
                            toolName,
                            result.path("success").asBoolean(),
                            result.path("errorCode").asString(""),
                            result.path("message").asString(""));
                }
                log.info("mcp.client.complete tool={} attempt={} durationMs={}", toolName, attempt, elapsedMs(started));
                return result;
            } catch (Exception exception) {
                last = exception;
                log.warn("mcp.client.retry tool={} attempt={} error={}", toolName, attempt, exception.getMessage());
            }
        }
        throw last;
    }

    private JsonNode responseTree(String response) throws Exception {
        JsonNode result = objectMapper.readTree(response);
        if (result.isString()) {
            result = objectMapper.readTree(result.asString());
        }
        if (result.isArray() && !result.isEmpty() && result.get(0).has("text")) {
            result = objectMapper.readTree(result.get(0).path("text").asString());
        }
        if (result.has("content") && result.path("content").isArray()
                && !result.path("content").isEmpty()) {
            JsonNode content = result.path("content").get(0);
            if (content.has("text")) {
                result = objectMapper.readTree(content.path("text").asString());
            }
        }
        return result;
    }

    private ToolCallback find(String toolName) {
        for (ToolCallback callback : toolCallbackProvider.getToolCallbacks()) {
            if (toolName.equals(callback.getToolDefinition().name())
                    || callback.getToolDefinition().name().endsWith(toolName)) {
                return callback;
            }
        }
        throw new IllegalStateException("MCP server does not expose " + toolName + ".");
    }

    private long elapsedMs(long started) {
        return (System.nanoTime() - started) / 1_000_000L;
    }
}
