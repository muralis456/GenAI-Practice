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
import tools.jackson.databind.JsonNode;

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
                            + "If you call Tavily search, you MUST pass a non-empty query string; never omit query. "
                            + "Return JSON only with shape "
                            + "{\"research\":[{\"topic\":\"\",\"summary\":\"\"}],"
                            + "\"attractions\":[{\"name\":\"\",\"description\":\"\",\"area\":\"\"}]}. "
                            + "Use plain ASCII in JSON strings. Do not put raw quotation marks inside summary text. "
                            + "Use weather to prefer indoor ideas when rain is likely.",
                    "Destination=" + destination + ", style=" + state.travelStyle()
                            + "\nReplan guidance: " + state.replanGuidance()
                            + "\nWeather: " + weather.toDisplay() + "\n" + rawResearch,
                    tavilySearchTool, weatherTool);
        } catch (Exception exception) {
            log.warn("Research LLM failed for destination={}", destination, exception);
            content = rawResearch;
        }

        ResearchExtraction extraction = parseExtraction(content, destination, rawResearch);
        List<TravelResearch> research = new ArrayList<>(
                extraction.getResearch() == null ? List.of() : extraction.getResearch());
        research.removeIf(item -> item == null || JsonSupport.looksLikeJsonObject(item.getSummary()));
        research.add(0, new TravelResearch("Weather", weather.toDisplay()));
        extraction.setResearch(research);
        if (extraction.getAttractions() == null) {
            extraction.setAttractions(List.of());
        }
        return new ResearchResult(extraction, weather);
    }

    private ResearchExtraction parseExtraction(String content, String destination, String rawResearch) {
        return jsonSupport.read(content, ResearchExtraction.class)
                .filter(this::hasUsefulContent)
                .or(() -> fromTree(content))
                .orElseGet(() -> plaintextFallback(destination, rawResearch, content));
    }

    private boolean hasUsefulContent(ResearchExtraction extraction) {
        boolean hasResearch = extraction.getResearch() != null && extraction.getResearch().stream()
                .anyMatch(item -> item != null
                        && !TravelState.isBlank(item.getSummary())
                        && !JsonSupport.looksLikeJsonObject(item.getSummary()));
        boolean hasAttractions = extraction.getAttractions() != null && extraction.getAttractions().stream()
                .anyMatch(item -> item != null && !TravelState.isBlank(item.getName()));
        return hasResearch || hasAttractions;
    }

    private java.util.Optional<ResearchExtraction> fromTree(String content) {
        return jsonSupport.readTree(content).map(this::fromNode).filter(this::hasUsefulContent);
    }

    private ResearchExtraction fromNode(JsonNode root) {
        ResearchExtraction extraction = new ResearchExtraction();
        List<TravelResearch> research = new ArrayList<>();
        JsonNode researchNode = root.get("research");
        if (researchNode != null && researchNode.isArray()) {
            for (JsonNode node : researchNode) {
                String topic = text(node, "topic");
                String summary = text(node, "summary");
                if (!summary.isBlank() && !JsonSupport.looksLikeJsonObject(summary)) {
                    research.add(new TravelResearch(topic.isBlank() ? "Tip" : topic, summary));
                }
            }
        }
        List<TravelAttraction> attractions = new ArrayList<>();
        JsonNode attractionsNode = root.get("attractions");
        if (attractionsNode != null && attractionsNode.isArray()) {
            for (JsonNode node : attractionsNode) {
                String name = text(node, "name");
                if (!name.isBlank()) {
                    attractions.add(new TravelAttraction(name, text(node, "description"), text(node, "area")));
                }
            }
        }
        extraction.setResearch(research);
        extraction.setAttractions(attractions);
        return extraction;
    }

    private ResearchExtraction plaintextFallback(String destination, String rawResearch, String content) {
        ResearchExtraction fallback = new ResearchExtraction();
        // If content is broken JSON, still try to lift attractions/topics for the UI.
        return fromTree(content).filter(this::hasUsefulContent).orElseGet(() -> {
            String summary = readablePlaintext(rawResearch, content, destination);
            fallback.setResearch(List.of(new TravelResearch("Destination guide", summary)));
            fallback.setAttractions(List.of());
            return fallback;
        });
    }

    private String readablePlaintext(String rawResearch, String content, String destination) {
        if (!TravelState.isBlank(rawResearch) && !JsonSupport.looksLikeJsonObject(rawResearch)) {
            return trim(rawResearch, 900);
        }
        if (!TravelState.isBlank(content) && !JsonSupport.looksLikeJsonObject(content)) {
            return trim(content, 900);
        }
        return "Local tips and attractions for " + destination
                + " will be refined in the itinerary. (Structured research parse failed.)";
    }

    private static String text(JsonNode node, String field) {
        if (node == null || node.get(field) == null || node.get(field).isNull()) {
            return "";
        }
        return node.get(field).asText("").trim();
    }

    private static String trim(String value, int max) {
        String cleaned = value.replaceAll("\\s+", " ").trim();
        return cleaned.length() <= max ? cleaned : cleaned.substring(0, max) + "...";
    }

    public static final class ResearchResult {
        private final ResearchExtraction extraction;
        private final WeatherForecast weather;

        public ResearchResult(ResearchExtraction extraction, WeatherForecast weather) {
            this.extraction = extraction;
            this.weather = weather;
        }

        public ResearchExtraction extraction() {
            return extraction;
        }

        public WeatherForecast weather() {
            return weather;
        }
    }
}
