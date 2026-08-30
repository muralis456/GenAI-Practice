package com.example.travel.model;

import java.io.Serial;
import java.io.Serializable;

public class AgentStep implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String node;
    private String status;
    private String detail;
    private long durationMs;

    public AgentStep() {
    }

    public AgentStep(String node, String status, String detail) {
        this(node, status, detail, 0);
    }

    public AgentStep(String node, String status, String detail, long durationMs) {
        this.node = node;
        this.status = status;
        this.detail = detail;
        this.durationMs = durationMs;
    }

    public String getNode() {
        return node;
    }

    public void setNode(String node) {
        this.node = node;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }
}
