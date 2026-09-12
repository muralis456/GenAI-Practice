package com.example.travel.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Generic MCP client adapter.
 *
 * MCP tool names are intentionally not part of the domain-specific clients.
 * The MCP server advertises tools through ToolCallbackProvider; this class
 * asks an LLM to select a tool from the discovered MCP catalog and then invokes it.
 */
@Service
@ConditionalOnProperty(prefix = "travel.mcp.client", name = "enabled", havingValue = "true")
public class McpToolClient {

    private static final Logger log = LoggerFactory.getLogger(McpToolClient.class);

    private final ToolCallbackProvider toolCallbackProvider;
    private final ObjectMapper objectMapper;
    private final McpToolSelector toolSelector;
    private final java.util.Set<String> allowedTools;
    private final int maxAttempts;

    public McpToolClient(ToolCallbackProvider toolCallbackProvider,
                         ObjectMapper objectMapper,
                         McpToolSelector toolSelector,
                         @Value("${TRAVEL_MCP_CLIENT_ALLOWED_TOOLS:}") String allowedTools,
                         @Value("${travel.mcp.client.max-attempts:2}") int maxAttempts) {
        this.toolCallbackProvider = toolCallbackProvider;
        this.objectMapper = objectMapper;
        this.toolSelector = toolSelector;
        this.allowedTools = allowedTools == null || allowedTools.isBlank()
            ? Set.of()
            : Arrays.stream(allowedTools.split(","))
                .map(String::trim)
                .filter(tool -> !tool.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
        this.maxAttempts = Math.max(1, maxAttempts);
    }

    /**
     * Invoke a tool by its exact MCP name. Kept for infrastructure/debugging
     * and backward compatibility, but domain clients should prefer callByUserInput().
     */
    public JsonNode call(String toolName, Map<String, Object> arguments) throws Exception {
        if (!allowedTools.isEmpty() && !allowedTools.contains(toolName)) {
            throw new IllegalStateException("MCP tool is not allowed: " + toolName);
        }
        return invoke(toolName, findExact(toolName), arguments);
    }

    /**
     * Discover the MCP tools currently exposed by the connected server.
     * Spring AI populates these callbacks from the MCP tools/list response.
     */
    public List<ToolCallback> availableTools() {
        return Arrays.stream(toolCallbackProvider.getToolCallbacks()).toList();
    }

    /**
     * Ask the LLM to select the best discovered MCP tool for the actual user
     * task. The LLM receives only tool metadata (name/description); invocation
     * happens afterwards through the selected ToolCallback.
     */
    public JsonNode callByUserInput(String agentPurpose, String userInput,
                                    Map<String, Object> arguments) throws Exception {
        String enrichedTask = buildSelectionContext(agentPurpose, userInput, arguments);
        ToolCallback callback = findByUserInput(agentPurpose, enrichedTask);
        String toolName = callback.getToolDefinition().name();
        return invoke(toolName, callback, arguments);
    }

    private String buildSelectionContext(String agentPurpose, String userInput, Map<String, Object> arguments) {
        return "Agent purpose: " + safe(agentPurpose)
                + "\nUser task: " + safe(userInput)
                + "\nStructured arguments: " + String.valueOf(arguments == null ? Map.of() : arguments);
    }

    private String safe(String value) {
        return value == null ? "" : value.replace("\n", " ").trim();
    }

    public ToolCallback findByUserInput(String agentPurpose, String userInput) throws Exception {
        List<ToolCallback> candidates = availableTools().stream()
                .filter(this::isAllowed)
                .toList();
        if (candidates.isEmpty()) {
            throw new IllegalStateException("No MCP tools are available from the configured server.");
        }
        log.info("mcp.client.tool-catalog purpose='{}' candidates={}", agentPurpose,
                candidates.stream().map(c -> c.getToolDefinition().name()).toList());
        return toolSelector.select(agentPurpose, userInput, candidates);
    }

    /**
     * Backward-compatible capability API. The capability is now treated as
     * task context and the LLM selects the actual MCP tool from discovered
     * tools instead of this class matching names/descriptions heuristically.
     */
    public JsonNode callByCapability(String capability, Map<String, Object> arguments) throws Exception {
        return callByUserInput("MCP capability: " + capability,
                capability + "\nArguments: " + arguments, arguments);
    }

    private JsonNode invoke(String toolName, ToolCallback callback, Map<String, Object> arguments) throws Exception {
        Exception last = null;
        long started = System.nanoTime();
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                log.info("mcp.client.invocation-start tool={} attempt={} purpose=dynamic-mcp-selection",
                        callback.getToolDefinition().name(), attempt);
                String response = callback.call(objectMapper.writeValueAsString(arguments));
                JsonNode result = responseTree(response);
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
                log.error("MCP tool invocation failed. tool={}", toolName, exception);
                last = exception;
                if (!isRetryable(exception) || attempt == maxAttempts) {
                    break;
                }
                log.warn("mcp.client.retry tool={} attempt={} error={}", toolName, attempt, exception.getMessage());
            }
        }
        throw last;
    }

    private boolean isAllowed(ToolCallback callback) {
        return allowedTools.isEmpty() || allowedTools.contains(callback.getToolDefinition().name());
    }

    private ToolCallback findExact(String toolName) {
        return availableTools().stream()
                .filter(this::isAllowed)
                .filter(callback -> toolName.equals(callback.getToolDefinition().name()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "MCP server does not expose allowed tool: " + toolName));
    }

    private boolean isRetryable(Exception exception) {
        return !(exception instanceof IllegalArgumentException || exception instanceof IllegalStateException);
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

    private long elapsedMs(long started) {
        return (System.nanoTime() - started) / 1_000_000L;
    }

}
