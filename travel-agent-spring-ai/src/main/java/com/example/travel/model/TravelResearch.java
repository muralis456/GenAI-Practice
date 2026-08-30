package com.example.travel.model;

import java.io.Serial;
import java.io.Serializable;

public class TravelResearch implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String topic;
    private String summary;

    public TravelResearch() {
    }

    public TravelResearch(String topic, String summary) {
        this.topic = topic;
        this.summary = summary;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String toDisplay() {
        if (topic == null || topic.isBlank()) {
            return summary == null ? "" : summary;
        }
        return topic + ": " + (summary == null ? "" : summary);
    }
}
