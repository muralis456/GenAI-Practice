package com.example.travel.graph;

import java.util.ArrayList;
import java.util.List;

/**
 * Picks the next graph node so unused specialists are never scheduled.
 * Native LangGraph fan-out uses {@link TravelGraphNodes#FAN_OUT} with parallel executors;
 * each specialist node no-ops when its {@code needs*} flag is false.
 */
public final class SpecialistRouter {

    private SpecialistRouter() {
    }

    public static String afterPlanner(TravelState state) {
        if (state.needsFlights()) {
            return TravelGraphNodes.AIRPORT;
        }
        if (anySpecialist(state)) {
            return TravelGraphNodes.FAN_OUT;
        }
        return TravelGraphNodes.SUPERVISOR;
    }

    public static String afterAirport(TravelState state) {
        return anySpecialist(state) ? TravelGraphNodes.FAN_OUT : TravelGraphNodes.SUPERVISOR;
    }

    public static String afterSupervisor(TravelState state) {
        if (TravelGraphNodes.ROUTE_RETRY.equalsIgnoreCase(state.supervisorDecision())) {
            return TravelGraphNodes.REPLAN;
        }
        if (state.needsBudget()) {
            return TravelGraphNodes.BUDGET;
        }
        if (state.needsItinerary()) {
            return TravelGraphNodes.ITINERARY;
        }
        return TravelGraphNodes.VALIDATOR;
    }

    public static boolean anySpecialist(TravelState state) {
        return state.needsFlights() || state.needsHotels() || state.needsResearch() || state.needsWeather();
    }

    public static List<String> plannedSpecialists(TravelState state) {
        List<String> nodes = new ArrayList<>();
        if (state.needsFlights()) {
            nodes.add(TravelGraphNodes.FLIGHT);
        }
        if (state.needsHotels()) {
            nodes.add(TravelGraphNodes.HOTEL);
        }
        if (state.needsResearch()) {
            nodes.add(TravelGraphNodes.RESEARCH);
        }
        if (state.needsWeather()) {
            nodes.add(TravelGraphNodes.WEATHER);
        }
        return nodes;
    }
}
