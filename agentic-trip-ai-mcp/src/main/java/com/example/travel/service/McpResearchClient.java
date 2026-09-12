package com.example.travel.service;

import com.example.travel.model.SearchHit;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
@ConditionalOnProperty(prefix = "travel.mcp.client", name = "enabled", havingValue = "true")
public class McpResearchClient {

    private static final Logger log = LoggerFactory.getLogger(McpResearchClient.class);
    private final McpToolClient client;

    public McpResearchClient(McpToolClient client) {
        this.client = client;
    }

    public List<SearchHit> search(String query) {
        try {
            JsonNode root = client.callByUserInput("Travel web research and destination information", query == null ? "" : query, Map.of("query", query == null ? "" : query));
            List<SearchHit> hits = new ArrayList<>();
            for (JsonNode node : root.path("hits")) {
                hits.add(new SearchHit(node.path("title").asString(""),
                    node.path("content").asString(node.path("snippet").asString("")),
                    node.path("url").asString("")));
            }
            return hits;
        } catch (Exception exception) {
            log.warn("MCP research lookup failed query={} error={}", query, exception.getMessage());
            return List.of();
        }
    }
}
