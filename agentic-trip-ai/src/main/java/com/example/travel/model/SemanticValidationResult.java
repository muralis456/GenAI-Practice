package com.example.travel.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class SemanticValidationResult implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private ValidationStatus status = ValidationStatus.PASS;
    private double score = 1.0;
    private List<String> issues = new ArrayList<>();
    private List<String> recommendedActions = new ArrayList<>();

    public ValidationStatus getStatus() {
        return status;
    }

    public void setStatus(ValidationStatus status) {
        this.status = status == null ? ValidationStatus.PASS : status;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }

    public List<String> getIssues() {
        return issues;
    }

    public void setIssues(List<String> issues) {
        this.issues = issues == null ? new ArrayList<>() : issues;
    }

    public List<String> getRecommendedActions() {
        return recommendedActions;
    }

    public void setRecommendedActions(List<String> recommendedActions) {
        this.recommendedActions = recommendedActions == null ? new ArrayList<>() : recommendedActions;
    }

    public boolean failed() {
        return status == ValidationStatus.FAIL || score < 0.65;
    }
}
