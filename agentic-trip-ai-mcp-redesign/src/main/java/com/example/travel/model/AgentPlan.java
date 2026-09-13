package com.example.travel.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Canonical execution plan for one user turn. It replaces scattered boolean
 * decisions as the orchestration source of truth while keeping legacy flags
 * available for graph/checkpoint compatibility.
 */
public class AgentPlan implements Serializable {
    @Serial private static final long serialVersionUID = 1L;

    private String goal = "GENERAL";
    private int version = 1;
    private boolean selectiveExecution;
    private List<AgentTask> tasks = new ArrayList<>();

    public AgentPlan() {}

    public static AgentPlan fromIntent(IntentPlan intent) {
        AgentPlan plan = new AgentPlan();
        if (intent == null) return plan;
        plan.goal = intent.getRequestType();

        if (intent.isNeedsFlights()) plan.tasks.add(new AgentTask("flights", "flight", true, "airport"));
        if (intent.isNeedsHotels()) plan.tasks.add(new AgentTask("hotels", "hotel", true));
        if (intent.isNeedsResearch()) plan.tasks.add(new AgentTask("research", "research", true));
        if (intent.isNeedsWeather()) plan.tasks.add(new AgentTask("weather", "weather", true));
        if (intent.isNeedsBudget()) plan.tasks.add(new AgentTask("budget", "budget", true, "flights", "hotels"));
        if (intent.isNeedsItinerary()) plan.tasks.add(new AgentTask("itinerary", "itinerary", true,
                "flights", "hotels", "weather", "research", "budget"));
        if (intent.isNeedsKnowledge()) plan.tasks.add(new AgentTask("knowledge", "rag", false));
        if (intent.isNeedsHistory()) plan.tasks.add(new AgentTask("history", "history", true));
        return plan;
    }

    public String getGoal() { return goal; }
    public void setGoal(String goal) { this.goal = goal == null ? "GENERAL" : goal; }
    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
    public boolean isSelectiveExecution() { return selectiveExecution; }
    public void setSelectiveExecution(boolean selectiveExecution) { this.selectiveExecution = selectiveExecution; }
    public List<AgentTask> getTasks() { return tasks; }
    public void setTasks(List<AgentTask> tasks) { this.tasks = tasks == null ? new ArrayList<>() : new ArrayList<>(tasks); }

    public AgentTask task(String id) {
        return tasks.stream().filter(t -> Objects.equals(t.getId(), id)).findFirst().orElse(null);
    }

    public boolean has(String id) { return task(id) != null; }

    public List<AgentTask> preSupervisorTasks() {
        return tasks.stream()
                .filter(t -> List.of("flights", "hotels", "research", "weather").contains(t.getId()))
                .filter(t -> !selectiveExecution || t.getStatus() == AgentTask.Status.READY)
                .toList();
    }

    /** Project a selective graph pass onto the canonical plan without changing the user's goal. */
    public void selectForExecution(List<String> taskIds) {
        selectiveExecution = true;
        tasks.forEach(task -> task.setStatus(taskIds != null && taskIds.contains(task.getId())
                ? AgentTask.Status.READY : AgentTask.Status.SKIPPED));
        version++;
    }

    public boolean ready(String id) {
        AgentTask task = task(id);
        if (task == null) return false;
        return task.getDependsOn().stream().allMatch(dep -> {
            AgentTask dependency = task(dep);
            // Airport is a graph prerequisite rather than a plan task.
            return dependency == null || dependency.getStatus() == AgentTask.Status.SUCCEEDED;
        });
    }

    public void markStarted(String id) {
        AgentTask task = task(id);
        if (task != null) {
            task.setStatus(AgentTask.Status.RUNNING);
            task.setAttempts(task.getAttempts() + 1);
        }
    }

    public void markSucceeded(String id) {
        AgentTask task = task(id);
        if (task != null) task.setStatus(AgentTask.Status.SUCCEEDED);
    }

    public void markFailed(String id, String reason) {
        AgentTask task = task(id);
        if (task != null) {
            task.setStatus(AgentTask.Status.FAILED);
            task.setFailureReason(reason);
        }
    }

    public boolean requiredWorkComplete() {
        return tasks.stream().filter(AgentTask::isRequired)
                .allMatch(t -> t.getStatus() == AgentTask.Status.SUCCEEDED || t.getStatus() == AgentTask.Status.SKIPPED);
    }

    public boolean hasRetryableFailure(int maxAttempts) {
        return tasks.stream().anyMatch(t -> t.isRequired()
                && t.getStatus() == AgentTask.Status.FAILED
                && t.getAttempts() < maxAttempts);
    }

    @Override public String toString() {
        return "AgentPlan{goal='" + goal + "', version=" + version + ", tasks=" + tasks + '}';
    }
}
