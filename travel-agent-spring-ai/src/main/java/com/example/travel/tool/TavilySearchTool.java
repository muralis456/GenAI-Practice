package com.example.travel.tool;

import com.example.travel.model.SearchHit;
import com.example.travel.service.ExternalApiService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TavilySearchTool {

    private final ExternalApiService externalApiService;

    public TavilySearchTool(ExternalApiService externalApiService) {
        this.externalApiService = externalApiService;
    }

    @Tool(description = "Web search via Tavily for destination research, attractions, food, and local tips.")
    public String search(@ToolParam(description = "Search query") String query) {
        return externalApiService.searchTravelInfo(query);
    }

    public List<SearchHit> searchHits(String query) {
        return externalApiService.searchTravelHits(query);
    }
}
