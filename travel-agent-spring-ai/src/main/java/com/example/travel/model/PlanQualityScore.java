package com.example.travel.model;

import java.io.Serial;
import java.io.Serializable;

public class PlanQualityScore implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public static final double PASS_THRESHOLD = 0.80;

    private double budget = 1.0;
    private double flight = 1.0;
    private double hotel = 1.0;
    private double itinerary = 1.0;
    private double preferences = 1.0;
    private double weather = 1.0;
    private double overall = 1.0;

    public double getBudget() {
        return budget;
    }

    public void setBudget(double budget) {
        this.budget = clamp(budget);
    }

    public double getFlight() {
        return flight;
    }

    public void setFlight(double flight) {
        this.flight = clamp(flight);
    }

    public double getHotel() {
        return hotel;
    }

    public void setHotel(double hotel) {
        this.hotel = clamp(hotel);
    }

    public double getItinerary() {
        return itinerary;
    }

    public void setItinerary(double itinerary) {
        this.itinerary = clamp(itinerary);
    }

    public double getPreferences() {
        return preferences;
    }

    public void setPreferences(double preferences) {
        this.preferences = clamp(preferences);
    }

    public double getWeather() {
        return weather;
    }

    public void setWeather(double weather) {
        this.weather = clamp(weather);
    }

    public double getOverall() {
        return overall;
    }

    public void setOverall(double overall) {
        this.overall = clamp(overall);
    }

    public boolean passes() {
        return overall >= PASS_THRESHOLD;
    }

    public void recomputeOverall() {
        overall = clamp((budget + flight + hotel + itinerary + preferences + weather) / 6.0);
    }

    private static double clamp(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, value));
    }
}
