package com.example.travel.service;



import com.example.travel.dto.AgentExecutionDetails;

import com.example.travel.dto.PlanValidationView;

import com.example.travel.dto.TripHeader;

import com.example.travel.dto.TripPlanResult;

import com.example.travel.graph.TravelState;

import com.example.travel.model.Itinerary;

import com.example.travel.model.PlanQualityScore;

import com.example.travel.model.ProvenanceEvent;

import com.example.travel.model.TripRequirements;

import org.springframework.stereotype.Service;



import java.util.ArrayList;

import java.util.LinkedHashSet;

import java.util.List;

import java.util.Locale;

import java.util.Set;



/**

 * Maps LangGraph {@link TravelState} into a domain presentation model for the UI/API.

 */

@Service

public class TripPlanAssembler {



    private final RequirementEvaluator requirementEvaluator;



    public TripPlanAssembler(RequirementEvaluator requirementEvaluator) {

        this.requirementEvaluator = requirementEvaluator;

    }



    public TripPlanResult assemble(TravelState state, String status, boolean awaitingApproval) {

        TripPlanResult result = new TripPlanResult();

        result.setTrip(buildHeader(state, status, awaitingApproval));

        if (state.includeFlightsInReport() && state.hasUsableFlights()) {

            result.setFlights(state.flights().stream()

                    .filter(f -> f != null && !"unavailable".equalsIgnoreCase(f.getStatus()))

                    .toList());

        }

        if (state.includeHotelsInReport() && state.hasHotelResults()) {

            result.setHotels(new ArrayList<>(state.hotels()));

        }

        if (state.includeItineraryInReport()) {

            Itinerary itinerary = state.itinerary();

            if (itinerary != null && !itinerary.isEmpty()) {

                result.setItinerary(itinerary);

            }

        }

        if (state.includeBudgetInReport() && state.budgetSummary() != null) {

            result.setBudget(state.budgetSummary());

        }

        if (state.includeWeatherInReport() && state.weather() != null) {

            result.setWeather(state.weather());

        }

        result.setValidation(buildValidation(state));

        return result;

    }



    public AgentExecutionDetails assembleExecution(TravelState state, String executionHistory) {

        AgentExecutionDetails details = new AgentExecutionDetails();

        details.setTimeline(new ArrayList<>(state.pipeline()));

        details.setSources(dedupeSources(state.provenance()));

        details.setExecutionHistory(executionHistory == null ? "" : executionHistory);
        details.setRagUsed(state.ragSufficient() || !state.ragContext().isBlank());
        details.setRagDecision(state.ragDecision());
        details.setRagQuery(state.ragQuery());
        details.setRagAnswer(state.ragAnswer());
        details.setRagSources(new ArrayList<>(state.ragSources()));
        details.setRagEvidenceScore(state.ragEvidenceScore());
        details.setRagCandidateCount(state.ragCandidateCount());
        details.setRagRerankedCount(state.ragRerankedCount());
        details.setRagIterations(state.ragIterations());
        details.setRagRetrievalMethod(state.ragRetrievalMethod());

        return details;

    }



    private TripHeader buildHeader(TravelState state, String status, boolean awaitingApproval) {

        TripHeader header = new TripHeader();

        header.setTitle(tripTitle(state.destination()));

        header.setOrigin(state.origin());

        header.setDestination(state.destination());

        header.setOriginIata(state.originIata());

        header.setDestinationIata(state.destinationIata());

        header.setDepartureDate(state.departureDate().toString());

        header.setReturnDate(state.returnDate().toString());

        header.setNights((int) state.nights());

        header.setTravelers(state.travelers());

        header.setTravelStyle(state.travelStyle());

        header.setBudgetLabel(state.budgetLabel());

        header.setAudienceLabel(audienceLabel(state));

        header.setStatus(status);

        header.setAwaitingApproval(awaitingApproval);

        PlanQualityScore quality = state.planQuality();

        if (quality != null && quality.getOverall() > 0) {

            header.setQualityScore((int) Math.round(quality.getOverall() * 100));

        }

        return header;

    }



    private PlanValidationView buildValidation(TravelState state) {

        PlanValidationView validation = new PlanValidationView();

        validation.setQuality(state.planQuality());

        validation.setErrors(new ArrayList<>(state.validationErrors()));

        validation.setSemanticNotes(new ArrayList<>(state.semanticNotes()));

        validation.setReviewItems(buildReviewItems(state));

        return validation;

    }



    private List<PlanValidationView.ReviewItem> buildReviewItems(TravelState state) {

        List<PlanValidationView.ReviewItem> items = new ArrayList<>();

        TripRequirements requirements = state.tripRequirements();



        if (requirements.isFamilyFriendly()) {

            double score = requirementEvaluator.familyScore(state, requirements);

            items.add(reviewItem(score >= 0.65, "Family-friendly activities",

                    "Itinerary lacks family-friendly activities"));

        }

        if (requirements.isFoodExperiences()) {

            double score = requirementEvaluator.foodScore(state, requirements);

            items.add(reviewItem(score >= 0.65, "Food experiences covered",

                    "Itinerary lacks food experiences"));

        }

        if (requirements.isLocalExperiences()) {

            double score = requirementEvaluator.localScore(state, requirements);

            items.add(reviewItem(score >= 0.65, "Local experiences included",

                    "Itinerary lacks local experiences"));

        }

        if (requirements.isBudgetConscious()) {

            items.add(new PlanValidationView.ReviewItem("ok", "Budget considered"));

        }

        if (state.budgetSummary() != null && state.budgetSummary().isWithinBudget()) {

            items.add(new PlanValidationView.ReviewItem("ok", "Within budget ceiling"));

        } else if (requirements.isBudgetConscious() && state.budgetSummary() != null) {

            items.add(new PlanValidationView.ReviewItem("warn", "Over budget ceiling"));

        }

        for (String note : state.semanticNotes()) {

            items.add(new PlanValidationView.ReviewItem("warn", note));

        }

        for (String error : state.validationErrors()) {

            items.add(new PlanValidationView.ReviewItem("warn", error));

        }

        if (items.isEmpty()) {

            items.add(new PlanValidationView.ReviewItem("ok", "Plan validated"));

        }

        return items;

    }



    private PlanValidationView.ReviewItem reviewItem(boolean passed, String okText, String warnText) {

        return passed

                ? new PlanValidationView.ReviewItem("ok", okText)

                : new PlanValidationView.ReviewItem("warn", warnText);

    }



    private List<String> dedupeSources(List<ProvenanceEvent> provenance) {

        Set<String> seen = new LinkedHashSet<>();

        List<String> lines = new ArrayList<>();

        for (ProvenanceEvent event : provenance) {

            if (event == null) {

                continue;

            }

            String line = event.toDisplay();

            if (seen.add(line)) {

                lines.add(line);

            }

        }

        return lines;

    }



    private String tripTitle(String destination) {

        String dest = TravelState.firstNonBlank(destination, "Trip");

        if (dest.toLowerCase(Locale.ROOT).contains("japan")) {

            return "Japan Trip";

        }

        return dest + " Trip";

    }



    private String audienceLabel(TravelState state) {

        String text = (state.userRequest() + " " + state.travelStyle()).toLowerCase(Locale.ROOT);

        if (text.contains("family")) {

            return "Family";

        }

        if (text.contains("couple")) {

            return "Couple";

        }

        if (text.contains("solo")) {

            return "Solo";

        }

        return state.travelers() > 1 ? state.travelers() + " travelers" : "1 traveler";

    }

}


