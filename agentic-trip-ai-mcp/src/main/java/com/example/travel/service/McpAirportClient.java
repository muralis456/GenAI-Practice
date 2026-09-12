package com.example.travel.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.util.Map;

@Service
@ConditionalOnProperty(prefix = "travel.mcp.client", name = "enabled", havingValue = "true")
public class McpAirportClient {

    private final McpToolClient client;

    public McpAirportClient(McpToolClient client) {
        this.client = client;
    }

    public String resolve(String cityOrCode) {
        try {
            JsonNode root = client.callByUserInput("Resolve an airport or IATA code", "Resolve airport/IATA for: " + (cityOrCode == null ? "" : cityOrCode), Map.of("cityOrCode", cityOrCode == null ? "" : cityOrCode));
            return root.path("iata").asString("");
        } catch (Exception exception) {
            return "";
        }
    }
}
