package com.example.travel.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "travel.models")
public class TravelModelsProperties {

    /**
     * Configured via {@code travel.models.*} in application.yml only — no code defaults.
     */
    private String extraction;
    private String planner;
    private String itinerary;
    private String finale;

    private double extractionTemperature = 0.1;
    private double plannerTemperature = 0.2;
    private double itineraryTemperature = 0.3;
    private double finaleTemperature = 0.4;

    public String getExtraction() {
        return extraction;
    }

    public void setExtraction(String extraction) {
        this.extraction = extraction;
    }

    public String getPlanner() {
        return planner;
    }

    public void setPlanner(String planner) {
        this.planner = planner;
    }

    public String getItinerary() {
        return itinerary;
    }

    public void setItinerary(String itinerary) {
        this.itinerary = itinerary;
    }

    public String getFinale() {
        return finale;
    }

    public void setFinale(String finale) {
        this.finale = finale;
    }

    public double getExtractionTemperature() {
        return extractionTemperature;
    }

    public void setExtractionTemperature(double extractionTemperature) {
        this.extractionTemperature = extractionTemperature;
    }

    public double getPlannerTemperature() {
        return plannerTemperature;
    }

    public void setPlannerTemperature(double plannerTemperature) {
        this.plannerTemperature = plannerTemperature;
    }

    public double getItineraryTemperature() {
        return itineraryTemperature;
    }

    public void setItineraryTemperature(double itineraryTemperature) {
        this.itineraryTemperature = itineraryTemperature;
    }

    public double getFinaleTemperature() {
        return finaleTemperature;
    }

    public void setFinaleTemperature(double finaleTemperature) {
        this.finaleTemperature = finaleTemperature;
    }

    public String model(AgentRole role) {
        String configured = switch (role) {
            case PLANNER -> planner;
            case EXTRACT -> extraction;
            case ITINERARY -> itinerary;
            case FINAL -> finale;
        };
        if (configured == null || configured.isBlank()) {
            throw new IllegalStateException(
                    "travel.models." + role.name().toLowerCase()
                            + " is not set. Configure it in application.yml (no model hardcoding in code).");
        }
        return configured.trim();
    }

    public double temperature(AgentRole role) {
        return switch (role) {
            case PLANNER -> plannerTemperature;
            case EXTRACT -> extractionTemperature;
            case ITINERARY -> itineraryTemperature;
            case FINAL -> finaleTemperature;
        };
    }

    public enum AgentRole {
        PLANNER,
        EXTRACT,
        ITINERARY,
        FINAL
    }
}
