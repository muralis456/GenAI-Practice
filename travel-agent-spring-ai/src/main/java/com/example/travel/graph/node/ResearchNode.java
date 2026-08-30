package com.example.travel.graph.node;

import com.example.travel.graph.TravelGraphNodes;
import com.example.travel.graph.TravelState;
import com.example.travel.graph.model.ResearchExtraction;
import com.example.travel.service.TravelResearchAgentService;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class ResearchNode implements NodeAction<TravelState> {

    private final TravelResearchAgentService travelResearchAgentService;

    public ResearchNode(TravelResearchAgentService travelResearchAgentService) {
        this.travelResearchAgentService = travelResearchAgentService;
    }

    @Override
    public Map<String, Object> apply(TravelState state) {
        TravelResearchAgentService.ResearchResult result = travelResearchAgentService.research(state);
        ResearchExtraction extraction = result.extraction();
        Map<String, Object> updates = new LinkedHashMap<>();
        updates.put(TravelState.RESEARCH, extraction.getResearch());
        updates.put(TravelState.ATTRACTIONS, extraction.getAttractions());
        updates.put(TravelState.WEATHER, result.weather());
        updates.putAll(TravelState.trace(TravelGraphNodes.RESEARCH, "ok",
                result.weather() != null && result.weather().isRainLikely() ? "rain likely" : "research complete"));
        return updates;
    }
}
