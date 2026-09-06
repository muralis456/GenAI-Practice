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

import java.util.Map;
import java.util.Set;

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
                         @Value("${travel.mcp.client.allowed-tools:search_flights,search_hotels,get_weather,resolve_airport,search_travel_research}") String allowedTools,
                         @Value("${travel.mcp.client.max-attempts:2}") int maxAttempts) {
        this.toolCallbackProvider = toolCallbackProvider;
        this.objectMapper = objectMapper;
        this.allowedTools = Set.of(allowedTools.split(","));
        this.maxAttempts = Math.max(1, maxAttempts);
    }

    public JsonNode call(String toolName, Map<String, Object> arguments) throws Exception {
        if (!allowedTools.contains(toolName)) {
            throw new IllegalStateException("MCP tool is not allowed: " + toolName);
        }
        ToolCallback callback = find(toolName);
        Exception last = null;
        long started = System.nanoTime();
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                JsonNode result = objectMapper.readTree(callback.call(objectMapper.writeValueAsString(arguments)));
                log.info("mcp.client.complete tool={} attempt={} durationMs={}", toolName, attempt, elapsedMs(started));
                return result;
            } catch (Exception exception) {
                last = exception;
                log.warn("mcp.client.retry tool={} attempt={} error={}", toolName, attempt, exception.getMessage());
            }
        }
        throw last;
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
