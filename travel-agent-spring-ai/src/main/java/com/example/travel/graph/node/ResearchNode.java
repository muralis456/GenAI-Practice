package com.example.travel.graph.node;

import com.example.travel.agent.TravelResearchAgentService;
import com.example.travel.graph.TravelGraphNodes;
import com.example.travel.graph.TravelState;
import com.example.travel.graph.model.ResearchExtraction;
import com.example.travel.model.ProvenanceEvent;
import com.example.travel.model.SearchHit;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ResearchNode implements NodeAction<TravelState> {

    private final TravelResearchAgentService travelResearchAgentService;

    public ResearchNode(TravelResearchAgentService travelResearchAgentService) {
        this.travelResearchAgentService = travelResearchAgentService;
    }

    @Override
    public Map<String, Object> apply(TravelState state) {
        if (!state.needsResearch()) {
            Map<String, Object> skip = new LinkedHashMap<>();
            skip.putAll(TravelState.trace(TravelGraphNodes.RESEARCH, "skip", "not requested"));
            return skip;
        }
        TravelResearchAgentService.ResearchResult result = travelResearchAgentService.research(state);
        ResearchExtraction extraction = result.extraction();
        Map<String, Object> updates = new LinkedHashMap<>();
        updates.put(TravelState.RESEARCH, extraction.getResearch());
        updates.put(TravelState.ATTRACTIONS, extraction.getAttractions());
        updates.putAll(TravelState.trace(TravelGraphNodes.RESEARCH, "ok", "research complete"));
        List<ProvenanceEvent> events = new ArrayList<>();
        for (SearchHit hit : result.hits()) {
            events.add(new ProvenanceEvent("research", "Tavily",
                    hit.getUrl() == null ? "" : hit.getUrl(),
                    hit.getScore(),
                    hit.getTitle()));
        }
        if (events.isEmpty()) {
            events.add(new ProvenanceEvent("research", "Tavily", "", 0, state.destination()));
        }
        updates.put(TravelState.PROVENANCE, events);
        return updates;
    }
}
