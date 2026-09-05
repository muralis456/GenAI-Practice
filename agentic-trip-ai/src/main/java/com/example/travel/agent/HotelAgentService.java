package com.example.travel.agent;

import com.example.travel.config.TravelModelsProperties.AgentRole;
import com.example.travel.graph.TravelState;
import com.example.travel.graph.model.HotelExtraction;
import com.example.travel.model.HotelOption;
import com.example.travel.model.SearchHit;
import com.example.travel.service.RoutedLlm;
import com.example.travel.support.JsonSupport;
import com.example.travel.tool.HotelSearchTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Hotel agent.
 *
 * Two-step approach:
 *
 * 1. LLM + HotelSearchTool -> research
 * 2. LLM without tools -> JSON
 *
 * This avoids combining Groq tool calling with JSON generation
 * in the same request.
 */
@Service
public class HotelAgentService {

    private static final Logger log =
            LoggerFactory.getLogger(HotelAgentService.class);

    private final RoutedLlm routedLlm;
    private final HotelSearchTool hotelSearchTool;
    private final JsonSupport jsonSupport;

    public HotelAgentService(
            RoutedLlm routedLlm,
            HotelSearchTool hotelSearchTool,
            JsonSupport jsonSupport) {

        this.routedLlm = routedLlm;
        this.hotelSearchTool = hotelSearchTool;
        this.jsonSupport = jsonSupport;
    }

    public HotelSearchResult search(TravelState state) {

        String destination = state.destination();
        boolean cheaper = state.hotelCheaper();

        log.info(
                "Hotel agent destination={} cheaper={}",
                destination,
                cheaper
        );

        String user =
                "Destination=" + destination
                        + "\nCheaper=" + cheaper
                        + "\nStyle=" + state.travelStyle()
                        + "\nTravelers=" + state.travelers()
                        + "\nReplan notes=" + state.replanNotes();

        /*
         * =========================================================
         * STEP 1: HOTEL RESEARCH
         * =========================================================
         *
         * Tool is enabled.
         *
         * IMPORTANT:
         * Do not ask for JSON in this request.
         */
        String researchSystem =
                "You are the Hotel Research Agent. "
                        + "Find fresh hotel information for the requested destination. "
                        + "Use HotelSearchTool when fresh hotel data is required. "
                        + "Prefer 2-4 real hotel names. "
                        + "Collect hotel name, area, price range, rating, "
                        + "family suitability and useful notes. "
                        + "After completing the search, return the findings as normal text. "
                        + "Do not return JSON.";

        String researchContent = "";

        try {

            researchContent = routedLlm.complete(
                    AgentRole.EXTRACT,
                    researchSystem,
                    user,
                    hotelSearchTool
            );

        } catch (Exception exception) {

            log.warn(
                    "Hotel research LLM failed for destination={}",
                    destination,
                    exception
            );
        }

        /*
         * If the research call failed, return the existing fallback.
         */
        if (researchContent == null || researchContent.isBlank()) {

            return fallback(destination, cheaper);
        }

        /*
         * =========================================================
         * STEP 2: STRUCTURE THE RESULT
         * =========================================================
         *
         * IMPORTANT:
         * No HotelSearchTool is supplied here.
         *
         * This is what prevents:
         *
         * "attempted to call tool 'json'"
         */
        String extractionSystem =
                "You are a Hotel Data Extraction Agent. "
                        + "Convert the hotel research into valid JSON only. "
                        + "Do not call any tools. "
                        + "Do not use markdown code fences. "
                        + "Use exactly this JSON structure: "
                        + "{\"hotels\":["
                        + "{\"name\":\"\","
                        + "\"area\":\"\","
                        + "\"priceRange\":\"\","
                        + "\"rating\":\"\","
                        + "\"suitableFor\":\"\","
                        + "\"notes\":\"\"}"
                        + "]}. "
                        + "Use only information present in the research. "
                        + "Do not invent hotels or prices. "
                        + "Prefer 2-4 real hotels. "
                        + "If a field is unavailable, use an empty string. "
                        + "Never use placeholders such as "
                        + "'Not specified', 'N/A', 'unknown', or 'none'.";

        String extractionUser =
                "Destination=" + destination
                        + "\nCheaper=" + cheaper
                        + "\nStyle=" + state.travelStyle()
                        + "\nTravelers=" + state.travelers()
                        + "\n\nHotel research:\n"
                        + researchContent;

        String content = "";

        try {

            /*
             * No tool argument here.
             */
            content = routedLlm.complete(
                    AgentRole.EXTRACT,
                    extractionSystem,
                    extractionUser
            );

        } catch (Exception exception) {

            log.warn(
                    "Hotel extraction LLM failed for destination={}",
                    destination,
                    exception
            );
        }

        /*
         * =========================================================
         * STEP 3: EXISTING JSON SUPPORT
         * =========================================================
         */
        List<HotelOption> hotels =
                jsonSupport.read(
                        content,
                        HotelExtraction.class
                )
                .map(HotelExtraction::getHotels)
                .filter(list ->
                        list != null && !list.isEmpty()
                )
                .orElseGet(ArrayList::new)
                .stream()
                .map(this::scrubPlaceholders)
                .filter(hotel ->
                        hotel != null
                                && !TravelState.isBlank(
                                hotel.getName()
                        )
                )
                .toList();

        if (hotels.isEmpty()) {

            return fallback(destination, cheaper);
        }

        return new HotelSearchResult(
                new ArrayList<>(hotels),
                List.of()
        );
    }

    private HotelOption scrubPlaceholders(HotelOption hotel) {

        hotel.setArea(
                clean(hotel.getArea())
        );

        hotel.setPriceRange(
                clean(hotel.getPriceRange())
        );

        hotel.setRating(
                clean(hotel.getRating())
        );

        hotel.setSuitableFor(
                clean(hotel.getSuitableFor())
        );

        hotel.setNotes(
                clean(hotel.getNotes())
        );

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

        String normalized =
                value.trim().toLowerCase(Locale.ROOT);

        return normalized.equals("not specified")
                || normalized.equals("n/a")
                || normalized.equals("unknown")
                || normalized.equals("none");
    }

    private HotelSearchResult fallback(
            String destination,
            boolean cheaper) {

        HotelOption fallback = new HotelOption();

        fallback.setName(
                "Hotel options in " + destination
        );

        fallback.setArea(destination);

        fallback.setPriceRange(
                cheaper ? "budget" : "mid-range"
        );

        fallback.setNotes(
                "Hotel search did not return structured options; "
                        + "try modifying budget or style."
        );

        return new HotelSearchResult(
                new ArrayList<>(List.of(fallback)),
                List.of()
        );
    }

    public static final class HotelSearchResult {

        private final List<HotelOption> hotels;
        private final List<SearchHit> hits;

        public HotelSearchResult(
                List<HotelOption> hotels,
                List<SearchHit> hits) {

            this.hotels =
                    hotels == null
                            ? List.of()
                            : hotels;

            this.hits =
                    hits == null
                            ? List.of()
                            : hits;
        }

        public List<HotelOption> hotels() {
            return hotels;
        }

        public List<SearchHit> hits() {
            return hits;
        }
    }
}