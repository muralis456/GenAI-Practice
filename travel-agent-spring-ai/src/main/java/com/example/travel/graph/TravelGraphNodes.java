package com.example.travel.graph;

public final class TravelGraphNodes {

    public static final String PLANNER = "planner";
    public static final String AIRPORT = "airport";
    public static final String FLIGHT = "flight";
    public static final String RESEARCH = "research";
    public static final String HOTEL = "hotel";
    public static final String BUDGET = "budget";
    public static final String ITINERARY = "itinerary";
    public static final String VALIDATOR = "validator";
    public static final String REPLAN = "replan";
    public static final String FINAL = "final";
    /** Human-in-the-loop gate; graph interrupts before this node. */
    public static final String HITL = "hitl";
    public static final String COMPLETE = "complete";
    public static final String INTENT = "intent";

    public static final String ROUTE_VALID = "valid";
    public static final String ROUTE_INVALID = "invalid";
    public static final String ROUTE_UNDER = "under";
    public static final String ROUTE_OVER = "over";
    public static final String ROUTE_SKIP_ITINERARY = "skip_itinerary";
    public static final String ROUTE_APPROVE = "approve";
    public static final String ROUTE_MODIFY = "modify";

    private TravelGraphNodes() {
    }
}
