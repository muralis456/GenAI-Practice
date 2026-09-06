package com.example.travel.dto;

import com.example.travel.model.AgentStep;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Internal LangGraph observability — not shown in the main trip presentation.
 */
public class AgentExecutionDetails implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private List<AgentStep> timeline = new ArrayList<>();
    private List<String> sources = new ArrayList<>();
    private String executionHistory = "";

    public List<AgentStep> getTimeline() {
        return timeline;
    }

    public void setTimeline(List<AgentStep> timeline) {
        this.timeline = timeline == null ? new ArrayList<>() : timeline;
    }

    public List<String> getSources() {
        return sources;
    }

    public void setSources(List<String> sources) {
        this.sources = sources == null ? new ArrayList<>() : sources;
    }

    public String getExecutionHistory() {
        return executionHistory;
    }

    public void setExecutionHistory(String executionHistory) {
        this.executionHistory = executionHistory == null ? "" : executionHistory;
    }
}
