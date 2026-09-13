package com.example.travel.dto;



/**

 * API response: metadata + structured plan + execution observability.

 */

public class TravelPlanResponse {



    private String threadId;

    private String status;

    /** Semantic request type for dynamic UI rendering, e.g. WEATHER or TRIP_PLANNING. */
    private String requestType = "TRIP_PLANNING";

    /** True only when the request represents a persisted trip plan workflow. */
    private boolean tripPlanning;

    private boolean awaitingApproval;

    private String userId;

    private String model;

    private TripPlanResult plan = new TripPlanResult();

    private AgentExecutionDetails execution = new AgentExecutionDetails();



    public TravelPlanResponse() {

    }



    public String getThreadId() {

        return threadId;

    }



    public void setThreadId(String threadId) {

        this.threadId = threadId;

    }



    public String getStatus() {

        return status;

    }



    public void setStatus(String status) {

        this.status = status;

    }



    public String getRequestType() {
        return requestType;
    }

    public void setRequestType(String requestType) {
        this.requestType = requestType == null ? "GENERAL" : requestType;
    }

    public boolean isTripPlanning() {
        return tripPlanning;
    }

    public void setTripPlanning(boolean tripPlanning) {
        this.tripPlanning = tripPlanning;
    }

    public boolean isAwaitingApproval() {

        return awaitingApproval;

    }



    public void setAwaitingApproval(boolean awaitingApproval) {

        this.awaitingApproval = awaitingApproval;

    }



    public String getUserId() {

        return userId;

    }



    public void setUserId(String userId) {

        this.userId = userId;

    }



    public String getModel() {

        return model;

    }



    public void setModel(String model) {

        this.model = model;

    }



    public TripPlanResult getPlan() {

        return plan;

    }



    public void setPlan(TripPlanResult plan) {

        this.plan = plan == null ? new TripPlanResult() : plan;

    }



    public AgentExecutionDetails getExecution() {

        return execution;

    }



    public void setExecution(AgentExecutionDetails execution) {

        this.execution = execution == null ? new AgentExecutionDetails() : execution;

    }

}


