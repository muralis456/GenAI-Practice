package com.example.travel.graph;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Picks the next graph node so unused specialists are never scheduled.
 * Native LangGraph fan-out uses {@link TravelGraphNodes#FAN_OUT} with parallel
 * executors;
 * a single specialist bypasses fan-out entirely.
 */
public final class SpecialistRouter {

    private SpecialistRouter() {
    }

    public static String afterPlanner(TravelState state) {
        String next;
        String reason;
        if (state.runFlights()) {
            next = TravelGraphNodes.AIRPORT;
            reason = "needsFlights";
        } else {
            next = specialistEntry(state);
            reason = TravelGraphNodes.FAN_OUT.equals(next) ? "parallelSpecialists" : "directSpecialist";
        }
        GraphExecutionLogger.route(TravelGraphNodes.ROUTER, next, state, reason);
        return next;
    }

    public static String afterAirport(TravelState state) {
        String next = specialistEntry(state);
        GraphExecutionLogger.route(TravelGraphNodes.AIRPORT, next, state,
                TravelGraphNodes.FAN_OUT.equals(next) ? "airportResolved" : "directSpecialist");
        return next;
    }

    public static String afterSupervisor(TravelState state) {
        String next;
        String reason;
        if (TravelGraphNodes.ROUTE_RETRY.equalsIgnoreCase(state.supervisorDecision())
                && state.retryCount() < state.maxRetries()) {
            next = TravelGraphNodes.REPLAN;
            reason = "supervisorRetry";
        } else if (TravelGraphNodes.ROUTE_RETRY.equalsIgnoreCase(state.supervisorDecision())) {
            // This protects the graph edge even if a stale or faulty supervisor
            // decision asks for another retry after the retry budget is spent.
            next = nextAfterSupervisor(state);
            reason = "maxRetriesReached";
        } else {
            next = nextAfterSupervisor(state);
            reason = next == TravelGraphNodes.BUDGET ? "needsBudget"
                    : next == TravelGraphNodes.ITINERARY ? "needsItinerary" : "validateOnly";
        }
        GraphExecutionLogger.route(TravelGraphNodes.SUPERVISOR, next, state, reason);
        return next;
    }

    private static String nextAfterSupervisor(TravelState state) {
        if (state.runBudget()) {
            return TravelGraphNodes.BUDGET;
        }
        if (state.runItinerary()) {
            return TravelGraphNodes.ITINERARY;
        }
        return TravelGraphNodes.VALIDATOR;
    }

    /**
     * When only one specialist is needed, route directly to it instead of
     * scheduling all four fan-out branches.
     */
    public static String specialistEntry(TravelState state) {
        List<String> specialists = plannedSpecialists(state);
        if (specialists.isEmpty()) {
            return TravelGraphNodes.SUPERVISOR;
        }
        if (specialists.size() == 1) {
            return specialists.get(0);
        }
        return TravelGraphNodes.FAN_OUT;
    }

    public static Optional<String> soleSpecialist(TravelState state) {
        List<String> specialists = plannedSpecialists(state);
        return specialists.size() == 1 ? Optional.of(specialists.get(0)) : Optional.empty();
    }

    public static boolean anySpecialist(TravelState state) {
        return state.runFlights() || state.runHotels() || state.runResearch() || state.runWeather() || state.runBudget() || state.runItinerary();
    }

    public static List<String> plannedSpecialists(TravelState state) {
        List<String> nodes = new ArrayList<>();

        // These are the specialist nodes that execute BEFORE Supervisor.
        // Budget and Itinerary are downstream of Supervisor and must never
        // be selected as fan-out/entry specialists here.
        if (state.runFlights()) {
            nodes.add(TravelGraphNodes.FLIGHT);
        }
        if (state.runHotels()) {
            nodes.add(TravelGraphNodes.HOTEL);
        }
        if (state.runResearch()) {
            nodes.add(TravelGraphNodes.RESEARCH);
        }
        if (state.runWeather()) {
            nodes.add(TravelGraphNodes.WEATHER);
        }
        return nodes;
    }
}
