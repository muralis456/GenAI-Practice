package com.example.travel.mcp.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class McpToolGovernance {

    private final Set<String> allowedTools;

    public McpToolGovernance(@Value("${travel.mcp.allowed-tools:search_flights,search_hotels,get_weather,resolve_airport,search_travel_research}") String configuredTools) {
        this.allowedTools = Set.of(configuredTools.split(","));
    }

    public void check(String toolName) {
        if (!allowedTools.contains(toolName)) {
            throw new IllegalStateException("MCP tool is not allowed: " + toolName);
        }
    }
}
