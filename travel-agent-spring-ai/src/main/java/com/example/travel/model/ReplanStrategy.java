package com.example.travel.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class ReplanStrategy implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String reason = "";
    private Double targetReduction;
    private double expectedImpact;
    private List<String> actions = new ArrayList<>();
    private List<ReplanAction> resolvedActions = new ArrayList<>();
    private String priority = "hotel";

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Double getTargetReduction() {
        return targetReduction;
    }

    public void setTargetReduction(Double targetReduction) {
        this.targetReduction = targetReduction;
    }

    public double getExpectedImpact() {
        return expectedImpact;
    }

    public void setExpectedImpact(double expectedImpact) {
        this.expectedImpact = expectedImpact;
    }

    public List<String> getActions() {
        return actions;
    }

    public void setActions(List<String> actions) {
        this.actions = actions == null ? new ArrayList<>() : actions;
    }

    public List<ReplanAction> getResolvedActions() {
        return resolvedActions;
    }

    public void setResolvedActions(List<ReplanAction> resolvedActions) {
        this.resolvedActions = resolvedActions == null ? new ArrayList<>() : resolvedActions;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public boolean hasAction(String action) {
        if (actions == null) {
            return false;
        }
        String needle = action == null ? "" : action.toLowerCase();
        if (actions.stream().anyMatch(item -> item != null && item.toLowerCase().contains(needle))) {
            return true;
        }
        return resolvedActions != null && resolvedActions.stream()
                .anyMatch(resolved -> resolved.wireName().contains(needle));
    }

    public boolean hasResolved(ReplanAction action) {
        return resolvedActions != null && resolvedActions.contains(action);
    }
}
