package com.example.travel.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class WeatherForecast implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String location;
    private String summary;
    private boolean rainLikely;
    private List<DailyForecast> days = new ArrayList<>();

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

    public List<DailyForecast> getDays() {
        return days == null ? List.of() : days;
    }

    public void setDays(List<DailyForecast> days) {
        this.days = days == null ? new ArrayList<>() : new ArrayList<>(days);
    }

    public boolean isRainLikely() {
        return rainLikely;
    }

    public void setRainLikely(boolean rainLikely) {
        this.rainLikely = rainLikely;
    }

    public static class DailyForecast implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        private String date;
        private String condition;
        private String icon;
        private Double high;
        private Double low;
        private Integer rainProbability;

        public DailyForecast() {
        }

        public DailyForecast(String date, String condition, String icon, Double high, Double low, Integer rainProbability) {
            this.date = date;
            this.condition = condition;
            this.icon = icon;
            this.high = high;
            this.low = low;
            this.rainProbability = rainProbability;
        }

        public String getDate() { return date; }
        public void setDate(String date) { this.date = date; }
        public String getCondition() { return condition; }
        public void setCondition(String condition) { this.condition = condition; }
        public String getIcon() { return icon; }
        public void setIcon(String icon) { this.icon = icon; }
        public Double getHigh() { return high; }
        public void setHigh(Double high) { this.high = high; }
        public Double getLow() { return low; }
        public void setLow(Double low) { this.low = low; }
        public Integer getRainProbability() { return rainProbability; }
        public void setRainProbability(Integer rainProbability) { this.rainProbability = rainProbability; }
    }

    public String toDisplay() {
        return (location == null ? "" : location + ": ") + (summary == null ? "" : summary);
    }
}
