package com.example.travel.graph.node;

import com.example.travel.graph.TravelGraphNodes;
import com.example.travel.graph.TravelState;
import com.example.travel.model.BudgetLineItem;
import com.example.travel.model.BudgetSummary;
import com.example.travel.model.FlightOption;
import com.example.travel.model.HotelOption;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class FinalNode implements NodeAction<TravelState> {

    @Override
    public Map<String, Object> apply(TravelState state) {
        Map<String, Object> updates = new LinkedHashMap<>();
        updates.put(TravelState.AWAITING_APPROVAL, Boolean.TRUE);
        updates.put(TravelState.FINAL_PLAN, approvalCard(state));
        updates.putAll(TravelState.trace(TravelGraphNodes.FINAL, "ok", "awaiting human approval"));
        return updates;
    }

    private String approvalCard(TravelState state) {
        BudgetSummary budget = state.budgetSummary();
        StringBuilder sb = new StringBuilder();
        sb.append("Trip summary\n");
        sb.append(TravelState.firstNonBlank(state.origin(), "?")).append(" (")
                .append(TravelState.firstNonBlank(state.originIata(), "?"))
                .append(") → ")
                .append(TravelState.firstNonBlank(state.destination(), "?"))
                .append(" (")
                .append(TravelState.firstNonBlank(state.destinationIata(), "?"))
                .append(")\n");
        sb.append("Dates: ").append(state.departureDate()).append(" → ").append(state.returnDate())
                .append(" (").append(state.nights()).append(" nights)\n");
        sb.append("Travelers: ").append(state.travelers()).append(" · Style: ").append(state.travelStyle()).append("\n\n");

        sb.append("Estimated cost: ");
        if (budget != null && budget.getEstimatedCost() != null) {
            sb.append("₹").append(budget.getEstimatedCost());
        } else {
            sb.append(state.budgetLabel());
        }
        sb.append("\n");
        if (budget != null) {
            for (BudgetLineItem item : budget.getLineItems()) {
                sb.append("  ").append(item.getCategory()).append(": ₹").append(item.getAmountInr()).append('\n');
            }
            if (budget.getRemaining() != null) {
                sb.append("  Remaining: ₹").append(budget.getRemaining()).append('\n');
            }
        }

        sb.append("\nFlights:\n");
        if (state.flights().isEmpty()) {
            sb.append("  None\n");
        } else {
            for (FlightOption flight : state.flights().stream().limit(3).toList()) {
                sb.append("  - ").append(flight.toDisplay()).append('\n');
            }
        }

        sb.append("\nHotels:\n");
        if (state.hotels().isEmpty()) {
            sb.append("  None\n");
        } else {
            for (HotelOption hotel : state.hotels().stream().limit(3).toList()) {
                sb.append("  - ").append(hotel.toDisplay()).append('\n');
            }
        }

        if (state.weather() != null) {
            sb.append("\nWeather: ").append(state.weather().toDisplay()).append('\n');
        }

        sb.append("\n[ Approve ]    [ Modify ]\n");
        if (!state.validationErrors().isEmpty()) {
            sb.append("\nCaveats: ").append(String.join(" ", state.validationErrors()));
        }
        return sb.toString();
    }
}
