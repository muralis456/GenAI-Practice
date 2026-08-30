package com.example.travel.agent;

import com.example.travel.config.TravelModelsProperties.AgentRole;
import com.example.travel.graph.TravelState;
import com.example.travel.graph.model.HotelExtraction;
import com.example.travel.model.HotelOption;
import com.example.travel.model.SearchHit;
import com.example.travel.service.AgentExecutionBudget;
import com.example.travel.service.RoutedLlm;
import com.example.travel.support.JsonSupport;
import com.example.travel.tool.HotelSearchTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class HotelAgentService {

    private static final Logger log = LoggerFactory.getLogger(HotelAgentService.class);

    private final RoutedLlm routedLlm;
    private final HotelSearchTool hotelSearchTool;
    private final JsonSupport jsonSupport;
    private final AgentExecutionBudget executionBudget;

    public HotelAgentService(RoutedLlm routedLlm, HotelSearchTool hotelSearchTool, JsonSupport jsonSupport,
                             AgentExecutionBudget executionBudget) {
        this.routedLlm = routedLlm;
        this.hotelSearchTool = hotelSearchTool;
        this.jsonSupport = jsonSupport;
        this.executionBudget = executionBudget;
    }

    public HotelSearchResult search(TravelState state) {
        String destination = state.destination();
        boolean cheaper = state.hotelCheaper();
        log.info("Hotel agent searching destination={} cheaper={}", destination, cheaper);

        List<SearchHit> hits = hotelSearchTool.searchHits(destination, state.travelStyle(), cheaper);
        String rawResearch = formatHits(hits);
        if (TravelState.isBlank(rawResearch) && executionBudget.tavilyAvailable()) {
            rawResearch = hotelSearchTool.search(destination, state.travelStyle(), cheaper);
        } else if (TravelState.isBlank(rawResearch)) {
            rawResearch = "Hotel web search skipped: Tavily call budget exhausted for this graph run.";
        }
        String content;
        try {
            String system = "You are the Hotel Agent. Extract concrete hotel options from the research. "
                    + "Return JSON only with shape "
                    + "{\"hotels\":[{\"name\":\"\",\"area\":\"\",\"priceRange\":\"\",\"rating\":\"\",\"suitableFor\":\"\",\"notes\":\"\"}]}. "
                    + "Never use placeholders like 'Not specified' — leave a field empty or omit it. "
                    + "Prefer 2-4 real hotel names with area and rough price band when available. "
                    + "Do not invent exact availability.";
            String user = "Destination=" + destination + "\nCheaper=" + cheaper
                    + "\nStyle=" + state.travelStyle()
                    + "\nHotel research:\n" + rawResearch;
            if (hits.isEmpty() && executionBudget.tavilyAvailable()) {
                content = routedLlm.complete(AgentRole.EXTRACT, system, user, hotelSearchTool);
            } else {
                content = routedLlm.complete(AgentRole.EXTRACT, system, user);
            }
        } catch (Exception exception) {
            log.warn("Hotel LLM failed for destination={}", destination, exception);
            content = rawResearch;
        }
        final String rawContent = content;

        List<HotelOption> hotels = jsonSupport.read(rawContent, HotelExtraction.class)
                .map(HotelExtraction::getHotels)
                .filter(list -> list != null && !list.isEmpty())
                .orElseGet(ArrayList::new)
                .stream()
                .map(this::scrubPlaceholders)
                .filter(hotel -> !TravelState.isBlank(hotel.getName()))
                .toList();

        if (hotels.isEmpty()) {
            HotelOption fallback = new HotelOption();
            fallback.setName("Hotel options in " + destination);
            fallback.setArea(destination);
            fallback.setPriceRange(cheaper ? "budget" : "mid-range");
            String notes = rawContent;
            if (JsonSupport.looksLikeJsonObject(notes) || notes.length() > 400) {
                notes = "See hotel search results for " + destination + " (" + (cheaper ? "budget" : "mid-range") + ").";
            }
            fallback.setNotes(notes);
            hotels = List.of(fallback);
        }
        return new HotelSearchResult(new ArrayList<>(hotels), hits);
    }

    private String formatHits(List<SearchHit> hits) {
        if (hits == null || hits.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (SearchHit hit : hits) {
            sb.append(hit.getTitle() == null ? "" : hit.getTitle()).append('\n');
            if (hit.getUrl() != null && !hit.getUrl().isBlank()) {
                sb.append(hit.getUrl()).append('\n');
            }
            if (hit.getContent() != null) {
                sb.append(hit.getContent()).append('\n');
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private HotelOption scrubPlaceholders(HotelOption hotel) {
        hotel.setArea(clean(hotel.getArea()));
        hotel.setPriceRange(clean(hotel.getPriceRange()));
        hotel.setRating(clean(hotel.getRating()));
        hotel.setSuitableFor(clean(hotel.getSuitableFor()));
        hotel.setNotes(clean(hotel.getNotes()));
        if (isPlaceholder(hotel.getName())) {
            hotel.setName("");
        }
        return hotel;
    }

    private String clean(String value) {
        return isPlaceholder(value) ? "" : value;
    }

    private boolean isPlaceholder(String value) {
        if (value == null || value.isBlank()) {
            return true;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("not specified")
                || normalized.equals("n/a")
                || normalized.equals("unknown")
                || normalized.equals("none");
    }

    public static final class HotelSearchResult {
        private final List<HotelOption> hotels;
        private final List<SearchHit> hits;

        public HotelSearchResult(List<HotelOption> hotels, List<SearchHit> hits) {
            this.hotels = hotels == null ? List.of() : hotels;
            this.hits = hits == null ? List.of() : hits;
        }

        public List<HotelOption> hotels() {
            return hotels;
        }

        public List<SearchHit> hits() {
            return hits;
        }
    }
}
