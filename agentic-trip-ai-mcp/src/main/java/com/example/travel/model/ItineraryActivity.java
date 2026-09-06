package com.example.travel.model;

import java.io.Serial;
import java.io.Serializable;

/**
 * Structured activity for itinerary days (presentation-friendly, not free-form prose).
 */
public class ItineraryActivity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String name = "";
    private String type = "sightseeing";
    private String indoorOutdoor = "mixed";
    private boolean familyFriendly;
    private boolean foodExperience;
    private boolean localExperience;

    public ItineraryActivity() {
    }

    public ItineraryActivity(String name, String type, String indoorOutdoor,
                              boolean familyFriendly, boolean foodExperience, boolean localExperience) {
        this.name = name == null ? "" : name;
        this.type = type == null ? "sightseeing" : type;
        this.indoorOutdoor = indoorOutdoor == null ? "mixed" : indoorOutdoor;
        this.familyFriendly = familyFriendly;
        this.foodExperience = foodExperience;
        this.localExperience = localExperience;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name == null ? "" : name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type == null ? "sightseeing" : type;
    }

    public String getIndoorOutdoor() {
        return indoorOutdoor;
    }

    public void setIndoorOutdoor(String indoorOutdoor) {
        this.indoorOutdoor = indoorOutdoor == null ? "mixed" : indoorOutdoor;
    }

    public boolean isFamilyFriendly() {
        return familyFriendly;
    }

    public void setFamilyFriendly(boolean familyFriendly) {
        this.familyFriendly = familyFriendly;
    }

    public boolean isFoodExperience() {
        return foodExperience;
    }

    public void setFoodExperience(boolean foodExperience) {
        this.foodExperience = foodExperience;
    }

    public boolean isLocalExperience() {
        return localExperience;
    }

    public void setLocalExperience(boolean localExperience) {
        this.localExperience = localExperience;
    }

    public String toDisplay() {
        StringBuilder sb = new StringBuilder(name);
        if (!type.isBlank()) {
            sb.append(" [").append(type).append(']');
        }
        if (!indoorOutdoor.isBlank() && !"mixed".equalsIgnoreCase(indoorOutdoor)) {
            sb.append(" (").append(indoorOutdoor).append(')');
        }
        return sb.toString();
    }
}
