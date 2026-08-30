package com.example.travel.dto;

import com.example.travel.model.AgentStep;
import com.example.travel.model.BudgetSummary;

import java.util.ArrayList;
import java.util.List;

public class TravelPlanResponse {

    private String userId;
    private String destination;
    private String model;
    private String finalPlan;
    private String flightInsights;
    private String travelInsights;
    private String hotelInsights;
    private String itinerarySummary;
    private String status;
    private String threadId;
    private boolean awaitingApproval;
    private String routeSummary;
    private BudgetSummary budgetSummary;
    private List<AgentStep> pipeline = new ArrayList<>();
    private List<String> validationErrors = new ArrayList<>();

    public TravelPlanResponse() {
    }

    public TravelPlanResponse(String userId, String destination, String model, String finalPlan,
                             String flightInsights, String travelInsights, String hotelInsights, String itinerarySummary) {
        this.userId = userId;
        this.destination = destination;
        this.model = model;
        this.finalPlan = finalPlan;
        this.flightInsights = flightInsights;
        this.travelInsights = travelInsights;
        this.hotelInsights = hotelInsights;
        this.itinerarySummary = itinerarySummary;
        this.status = "COMPLETE";
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getDestination() {
        return destination;
    }

    public void setDestination(String destination) {
        this.destination = destination;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getFinalPlan() {
        return finalPlan;
    }

    public void setFinalPlan(String finalPlan) {
        this.finalPlan = finalPlan;
    }

    public String getFlightInsights() {
        return flightInsights;
    }

    public void setFlightInsights(String flightInsights) {
        this.flightInsights = flightInsights;
    }

    public String getTravelInsights() {
        return travelInsights;
    }

    public void setTravelInsights(String travelInsights) {
        this.travelInsights = travelInsights;
    }

    public String getItinerarySummary() {
        return itinerarySummary;
    }

    public void setItinerarySummary(String itinerarySummary) {
        this.itinerarySummary = itinerarySummary;
    }

    public String getHotelInsights() {
        return hotelInsights;
    }

    public void setHotelInsights(String hotelInsights) {
        this.hotelInsights = hotelInsights;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getThreadId() {
        return threadId;
    }

    public void setThreadId(String threadId) {
        this.threadId = threadId;
    }

    public boolean isAwaitingApproval() {
        return awaitingApproval;
    }

    public void setAwaitingApproval(boolean awaitingApproval) {
        this.awaitingApproval = awaitingApproval;
    }

    public String getRouteSummary() {
        return routeSummary;
    }

    public void setRouteSummary(String routeSummary) {
        this.routeSummary = routeSummary;
    }

    public BudgetSummary getBudgetSummary() {
        return budgetSummary;
    }

    public void setBudgetSummary(BudgetSummary budgetSummary) {
        this.budgetSummary = budgetSummary;
    }

    public List<AgentStep> getPipeline() {
        return pipeline;
    }

    public void setPipeline(List<AgentStep> pipeline) {
        this.pipeline = pipeline;
    }

    public List<String> getValidationErrors() {
        return validationErrors;
    }

    public void setValidationErrors(List<String> validationErrors) {
        this.validationErrors = validationErrors;
    }
}
