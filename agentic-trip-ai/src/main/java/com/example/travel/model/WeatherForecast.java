package com.example.travel.model;

import java.io.Serial;
import java.io.Serializable;

public class WeatherForecast implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String location;
    private String summary;
    private boolean rainLikely;

    public WeatherForecast() {
    }

    public WeatherForecast(String location, String summary, boolean rainLikely) {
        this.location = location;
        this.summary = summary;
        this.rainLikely = rainLikely;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public boolean isRainLikely() {
        return rainLikely;
    }

    public void setRainLikely(boolean rainLikely) {
        this.rainLikely = rainLikely;
    }

    public String toDisplay() {
        return (location == null ? "" : location + ": ") + (summary == null ? "" : summary);
    }
}
