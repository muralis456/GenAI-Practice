package com.example.travel.service;

import com.example.travel.config.TravelModelsProperties.AgentRole;
import com.example.travel.graph.TravelState;
import com.example.travel.graph.model.ResearchExtraction;
import com.example.travel.model.TravelAttraction;
import com.example.travel.model.TravelResearch;
import com.example.travel.model.WeatherForecast;
import com.example.travel.support.JsonSupport;
import com.example.travel.tool.TavilySearchTool;
import com.example.travel.tool.WeatherTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class TravelResearchAgentService {

    private static final Logger log = LoggerFactory.getLogger(TravelResearchAgentService.class);

    private final RoutedLlm routedLlm;
    private final TavilySearchTool tavilySearchTool;
    private final WeatherTool weatherTool;
    private final JsonSupport jsonSupport;

    public TravelResearchAgentService(RoutedLlm routedLlm,
                                      TavilySearchTool tavilySearchTool,
                                      WeatherTool weatherTool,
                                      JsonSupport jsonSupport) {
        this.routedLlm = routedLlm;
        this.tavilySearchTool = tavilySearchTool;
        this.weatherTool = weatherTool;
        this.jsonSupport = jsonSupport;
    }

    public ResearchResult research(TravelState state) {
        String destination = state.destination();
        // Keep Tavily query clean — never append validator/replan error text.
        boolean budgetMode = state.retryCount() > 0
                || "budget".equalsIgnoreCase(state.travelStyle());
        String query = (budgetMode
                ? "Best budget attractions, food, local tips for "
                : "Best attractions, food, local tips for ")
                + destination + " with a " + state.travelStyle() + " travel style.";
        log.info("Research agent searching destination={}", destination);
        String rawResearch = tavilySearchTool.search(query);
        WeatherForecast weather = weatherTool.forecast(destination, state.departureDate(), state.returnDate());
        String content;
        try {
            content = routedLlm.complete(AgentRole.EXTRACT,
                    "You are the Travel Research Agent. Prefer the supplied research and weather. "
                            + "You may call Tavily or weather tools if more detail is needed. "
                            + "Return JSON only with shape "
                            + "{\"research\":[{\"topic\":\"\",\"summary\":\"\"}],"
                            + "\"attractions\":[{\"name\":\"\",\"description\":\"\",\"area\":\"\"}]}. "
                            + "Use weather to prefer indoor ideas when rain is likely.",
                    "Destination=" + destination + ", style=" + state.travelStyle()
                            + "\nReplan guidance: " + state.replanGuidance()
                            + "\nWeather: " + weather.toDisplay() + "\n" + rawResearch,
                    tavilySearchTool, weatherTool);
        } catch (Exception exception) {
            log.warn("Research LLM failed for destination={}", destination, exception);
            content = rawResearch;
        }
        final String rawContent = content;

        ResearchExtraction extraction = jsonSupport.read(rawContent, ResearchExtraction.class).orElseGet(() -> {
            ResearchExtraction fallback = new ResearchExtraction();
            fallback.setResearch(List.of(new TravelResearch("Destination guide", rawContent)));
            fallback.setAttractions(List.of(new TravelAttraction(destination, "See destination guide.", "")));
            return fallback;
        });
        List<TravelResearch> research = new ArrayList<>(extraction.getResearch() == null ? List.of() : extraction.getResearch());
        research.add(0, new TravelResearch("Weather", weather.toDisplay()));
        extraction.setResearch(research);
        if (extraction.getAttractions() == null) {
            extraction.setAttractions(List.of());
        }
        return new ResearchResult(extraction, weather);
    }

    public record ResearchResult(ResearchExtraction extraction, WeatherForecast weather) {
    }
}
