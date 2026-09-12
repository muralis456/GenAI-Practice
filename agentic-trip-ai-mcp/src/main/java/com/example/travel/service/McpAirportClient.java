package com.example.travel.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.util.Map;

@Service
@ConditionalOnProperty(prefix = "travel.mcp.client", name = "enabled", havingValue = "true")
public class McpAirportClient {

    private static final Logger log = LoggerFactory.getLogger(McpAirportClient.class);

    private final McpToolClient client;

    public McpAirportClient(McpToolClient client) {
        this.client = client;
    }

    public String resolve(String cityOrCode) {
        String value = cityOrCode == null ? "" : cityOrCode.trim();

        if (value.isEmpty()) {
            log.warn("MCP airport resolve skipped: cityOrCode is empty");
            return "";
        }

        try {
            log.info("MCP airport resolve start cityOrCode={}", value);

            JsonNode root = client.callByUserInput(
                    "Resolve an airport or IATA code",
                    "Resolve airport/IATA for: " + value,
                    Map.of("cityOrCode", value));

            String iata = root.path("iata").asString("");

            log.info("MCP airport resolve complete cityOrCode={} iata={}", value, iata);
            return iata;
        } catch (Exception exception) {
            log.warn(
                    "MCP airport resolve failed cityOrCode={} error={}",
                    value,
                    exception.getMessage(),
                    exception);
            return "";
        }
    }
}
