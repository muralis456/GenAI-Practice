package com.example.travel.tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Central MCP policy enforcement. The LLM can select a tool, but never grants permission. */
@Service
public class ToolGovernanceService {
    private static final Logger log = LoggerFactory.getLogger(ToolGovernanceService.class);

    private final boolean enabled;
    private final int maxArgumentBytes;
    private final Map<String, Long> cooldownUntil = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> failures = new ConcurrentHashMap<>();
    private final int circuitFailureThreshold;
    private final long circuitOpenMs;

    public ToolGovernanceService(
            @Value("${travel.mcp.governance.enabled:true}") boolean enabled,
            @Value("${travel.mcp.governance.max-argument-bytes:16384}") int maxArgumentBytes,
            @Value("${travel.mcp.governance.circuit-failure-threshold:3}") int circuitFailureThreshold,
            @Value("${travel.mcp.governance.circuit-open-ms:30000}") long circuitOpenMs) {
        this.enabled = enabled;
        this.maxArgumentBytes = Math.max(1024, maxArgumentBytes);
        this.circuitFailureThreshold = Math.max(1, circuitFailureThreshold);
        this.circuitOpenMs = Math.max(1000, circuitOpenMs);
    }

    public void authorize(String toolName, String agentPurpose, String argumentsJson) {
        if (!enabled) return;
        if (toolName == null || toolName.isBlank()) throw new SecurityException("MCP tool name is required");
        if (argumentsJson != null && argumentsJson.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > maxArgumentBytes) {
            throw new SecurityException("MCP tool arguments exceed the configured safety limit");
        }
        String key = key(toolName);
        Long until = cooldownUntil.get(key);
        if (until != null && until > System.currentTimeMillis()) {
            throw new IllegalStateException("MCP tool circuit is open: " + toolName);
        }
        if (agentPurpose != null && agentPurpose.toLowerCase().contains("system")) {
            throw new SecurityException("Invalid MCP agent purpose");
        }
        log.debug("mcp.governance.authorized tool={} purpose={}", toolName, abbreviate(agentPurpose));
    }

    public void recordSuccess(String toolName) {
        failures.remove(key(toolName));
        cooldownUntil.remove(key(toolName));
    }

    public void recordFailure(String toolName) {
        String key = key(toolName);
        int count = failures.computeIfAbsent(key, ignored -> new AtomicInteger()).incrementAndGet();
        if (count >= circuitFailureThreshold) {
            cooldownUntil.put(key, System.currentTimeMillis() + circuitOpenMs);
            log.warn("mcp.governance.circuit-open tool={} failures={} openMs={}", toolName, count, circuitOpenMs);
        }
    }

    private String key(String value) { return value == null ? "" : value.trim(); }
    private String abbreviate(String value) {
        if (value == null) return "";
        return value.length() <= 120 ? value : value.substring(0, 120) + "...";
    }
}
