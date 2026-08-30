package com.example.travel.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "travel.models")
public class TravelModelsProperties {

    /**
     * Slot extraction / JSON shaping (fast, cheap). Default Granite if pulled, else Llama.
     */
    private String extraction = "llama3.2:3b";

    /**
     * Planner reasoning. Prefer Qwen when available.
     */
    private String planner = "llama3.2:3b";

    /**
     * Itinerary drafting.
     */
    private String itinerary = "llama3.2:3b";

    /**
     * Final approved narrative. Prefer a stronger model when available.
     */
    private String finale = "llama3.2:3b";

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
        return switch (role) {
            case PLANNER -> planner;
            case EXTRACT -> extraction;
            case ITINERARY -> itinerary;
            case FINAL -> finale;
        };
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
