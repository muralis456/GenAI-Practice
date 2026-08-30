package com.example.travel.model;

import java.io.Serial;
import java.io.Serializable;

public class ItineraryDay implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private int day;
    private String title;
    private String activities;

    public ItineraryDay() {
    }

    public ItineraryDay(int day, String title, String activities) {
        this.day = day;
        this.title = title;
        this.activities = activities;
    }

    public int getDay() {
        return day;
    }

    public void setDay(int day) {
        this.day = day;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getActivities() {
        return activities;
    }

    public void setActivities(String activities) {
        this.activities = activities;
    }
}
