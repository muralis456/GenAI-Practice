package com.example.travel.model;

import java.io.Serial;
import java.io.Serializable;

public class SupervisorAssessment implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String decision = "proceed";
    private String reason = "";
    private double qualityHint = 1.0;
    private String suggestedStrategy = "";

    public String getDecision() {
        return decision;
    }

    public void setDecision(String decision) {
        this.decision = decision == null ? "proceed" : decision;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason == null ? "" : reason;
    }

    public double getQualityHint() {
        return qualityHint;
    }

    public void setQualityHint(double qualityHint) {
        this.qualityHint = qualityHint;
    }

    public String getSuggestedStrategy() {
        return suggestedStrategy;
    }

    public void setSuggestedStrategy(String suggestedStrategy) {
        this.suggestedStrategy = suggestedStrategy == null ? "" : suggestedStrategy;
    }
}
