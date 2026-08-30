package com.example.travel.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class HotelSearchTool {

    private final TavilySearchTool tavilySearchTool;

    public HotelSearchTool(TavilySearchTool tavilySearchTool) {
        this.tavilySearchTool = tavilySearchTool;
    }

    @Tool(description = "Search hotel and accommodation options for a destination. Set cheaper=true for budget stays.")
    public String search(
            @ToolParam(description = "Destination city or country") String destination,
            @ToolParam(description = "Travel style such as balanced, family, luxury, budget", required = false) String travelStyle,
            @ToolParam(description = "Prefer cheaper/budget hotels when true", required = false) Boolean cheaper) {
        boolean budget = cheaper != null && cheaper;
        String style = travelStyle == null || travelStyle.isBlank() ? "balanced" : travelStyle;
        String query = (budget ? "Budget affordable hotels in " : "Best hotels in ") + destination
                + " including location, price range, family suitability, and guest ratings. Style=" + style;
        return tavilySearchTool.search(query);
    }
}
