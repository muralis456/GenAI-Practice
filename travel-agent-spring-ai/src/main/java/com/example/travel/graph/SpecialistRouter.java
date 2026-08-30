package com.example.travel.graph;

import java.util.ArrayList;
import java.util.List;

/**
 * Picks the next graph node so unused specialists are never scheduled.
 * LangGraph4j 1.8 Command is single-destination, so a 4-way parallel fan-in
 * cannot legally omit unused incoming edges; needed specialists run in parallel
 * inside {@code specialists}, and this router skips that node entirely when none are needed.
 */
public final class SpecialistRouter {

    private SpecialistRouter() {
    }

    public static String afterPlanner(TravelState state) {
        if (state.needsFlights()) {
            return TravelGraphNodes.AIRPORT;
        }
        if (anySpecialist(state)) {
            return TravelGraphNodes.SPECIALISTS;
        }
        return TravelGraphNodes.SUPERVISOR;
    }

    public static String afterAirport(TravelState state) {
        return anySpecialist(state) ? TravelGraphNodes.SPECIALISTS : TravelGraphNodes.SUPERVISOR;
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
