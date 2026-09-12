package com.example.travel.agent;

import com.example.travel.config.TravelModelsProperties.AgentRole;
import com.example.travel.graph.TravelState;
import com.example.travel.graph.model.PlannerExtraction;
import com.example.travel.service.RoutedLlm;
import com.example.travel.support.JsonSupport;
import com.example.travel.support.TripSlotHeuristics;
import com.example.travel.tool.AirportLookupTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class PlannerAgentService {

    private static final Logger log = LoggerFactory.getLogger(PlannerAgentService.class);
    private static final Pattern ROUTE = Pattern.compile(
            "(?i)\\bfrom\\s+(.+?)\\s+to\\s+(.+?)(?:\\s+for\\s+|\\s+on\\s+|\\s+today\\b|[.!?]|$)");

    private final RoutedLlm routedLlm;
    private final JsonSupport jsonSupport;
    private final AirportLookupTool airportLookupTool;

    public PlannerAgentService(RoutedLlm routedLlm, JsonSupport jsonSupport, AirportLookupTool airportLookupTool) {
        this.routedLlm = routedLlm;
        this.jsonSupport = jsonSupport;
        this.airportLookupTool = airportLookupTool;
    }

    public Map<String, Object> plan(TravelState state) {
        log.info("Planner agent extracting trip slots from user request");
        String content;
        try {
            content = routedLlm.complete(AgentRole.PLANNER,
                    "You are the Planner Agent. Extract trip slots from the CURRENT REQUEST only. "
                            + "Ignore conflicting destinations in conversation history. Return JSON only: "
                            + "{\"origin\":\"\",\"destination\":\"\",\"departureDate\":\"yyyy-MM-dd\","
                            + "\"returnDate\":\"yyyy-MM-dd\",\"travelers\":1,\"budget\":\"\",\"travelStyle\":\"\"}. "
                            + "For country-only destinations like Japan, set destination to the main city (Tokyo). "
                            + "Preserve budgets like '2 lakh' or '₹200000'. Use empty strings when unknown. "
                            + "Never invent London/LHR unless the user said London. "
                            + "You may call the airport lookup tool to sanity-check city names.",
                    "CURRENT REQUEST: " + state.userRequest()
                            + "\nKnown origin: " + state.origin()
                            + "\nKnown destination: " + state.destination()
                            + "\nDeparture date: " + state.departureDate()
                            + "\nReturn date: " + state.returnDate()
                            + "\nTravelers: " + state.travelers()
                            + "\nBudget: " + state.budgetLabel()
                            + "\nStyle: " + state.travelStyle()
                            + "\nHistory (background only): " + state.historyContext(),
                    airportLookupTool);
        } catch (Exception exception) {
            log.warn("Planner LLM extraction failed, using request fields and regex", exception);
            content = "";
        }

        PlannerExtraction extraction = jsonSupport.read(content, PlannerExtraction.class).orElse(null);
        String origin = first(extraction == null ? null : extraction.getOrigin(),
                routeGroup(state.userRequest(), 1),
                state.origin(),
                state.preferredAirport(),
                "Bengaluru");
        // Deterministic destination hints from the CURRENT request take precedence
        // over an LLM slot that may contain a typo such as "dubaig". This prevents
        // downstream flight/hotel agents from receiving an invalid destination.
        String requestDestinationHint = TripSlotHeuristics.extractDestinationHint(state.userRequest());
        String destinationCandidate = first(requestDestinationHint,
                extraction == null ? null : extraction.getDestination(),
                routeGroup(state.userRequest(), 2),
                state.destination());
        final String destination = TripSlotHeuristics.normalizePlace(destinationCandidate);
        origin = TripSlotHeuristics.normalizePlace(origin);
        if (TravelState.isBlank(origin)) {
            origin = "Bengaluru";
        }

        LocalDate departure = TravelState.parseDate(extraction == null ? null : extraction.getDepartureDate(), state.departureDate());
        LocalDate returning = TravelState.parseDate(extraction == null ? null : extraction.getReturnDate(), state.returnDate());
        returning = TripSlotHeuristics.inferReturnDate(state.userRequest(), departure, returning);

        int travelers = extraction != null && extraction.getTravelers() != null
                ? Math.max(extraction.getTravelers(), 1)
                : state.travelers();
        String budgetLabel = first(extraction == null ? null : extraction.getBudget(),
                TripSlotHeuristics.extractBudgetLabel(state.userRequest()),
                state.budgetLabel());
        String travelStyle = first(extraction == null ? null : extraction.getTravelStyle(), state.travelStyle());
        BigDecimal budget = TravelState.parseBudget(budgetLabel);
        if (budget == null) {
            budget = state.budget();
        }
        if (budget == null) {
            budget = TravelState.UNSET_BUDGET;
        }

        log.info("Planner resolved origin={}, destination={}, dates={} to {}, budget={}",
                origin, destination, departure, returning, budgetLabel);

        Map<String, Object> updates = new LinkedHashMap<>();
        updates.put(TravelState.ORIGIN, origin);
        updates.put(TravelState.DESTINATION, destination);
        updates.put(TravelState.DEPARTURE_DATE, departure);
        updates.put(TravelState.RETURN_DATE, returning);
        updates.put(TravelState.TRAVELERS, travelers);
        updates.put(TravelState.BUDGET_LABEL, budgetLabel);
        updates.put(TravelState.BUDGET, budget);
        updates.put(TravelState.TRAVEL_STYLE, travelStyle);
        updates.put(TravelState.LAST_DECISION, new com.example.travel.model.AgentDecision(
                "planner",
                state.requestType(),
                "slots origin=" + origin + " dest=" + destination + " strategy=" + state.planStrategy(),
                0.9));
        return updates;
    }

    private String routeGroup(String request, int group) {
        Matcher matcher = ROUTE.matcher(request == null ? "" : request);
        if (matcher.find()) {
            return matcher.group(group).trim().replaceFirst("[.!?].*$", "");
        }
        return "";
    }

    private String first(String... values) {
        return TravelState.firstNonBlank(values);
    }
}
