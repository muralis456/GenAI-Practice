package com.example.travel.dto;

public class TravelPlanResponse {

    private String userId;
    private String destination;
    private String model;
    private String finalPlan;
    private String flightInsights;
    private String travelInsights;
    private String itinerarySummary;

    public TravelPlanResponse() {
    }

    public TravelPlanResponse(String userId, String destination, String model, String finalPlan,
                             String flightInsights, String travelInsights, String itinerarySummary) {
        this.userId = userId;
        this.destination = destination;
        this.model = model;
        this.finalPlan = finalPlan;
        this.flightInsights = flightInsights;
        this.travelInsights = travelInsights;
        this.itinerarySummary = itinerarySummary;
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
}
