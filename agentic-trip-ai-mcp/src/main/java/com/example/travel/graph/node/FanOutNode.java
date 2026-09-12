package com.example.travel.graph.node;

import com.example.travel.graph.GraphExecutionLogger;
import com.example.travel.graph.AgentStepEnricher;
import com.example.travel.graph.SpecialistRouter;
import com.example.travel.graph.TravelGraphNodes;
import com.example.travel.graph.TravelState;

import lombok.extern.slf4j.Slf4j;

import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Selective parallel fan-out. LangGraph4j's static parallel edges would execute
 * every branch, so this node performs the runtime selection and executes only
 * the requested specialists concurrently, then joins their updates once.
 */
@Component
@Slf4j 
public class FanOutNode implements NodeAction<TravelState> {

    private final FlightNode flightNode;
    private final HotelNode hotelNode;
    private final ResearchNode researchNode;
    private final WeatherNode weatherNode;
    private final Executor executor;

    public FanOutNode(FlightNode flightNode,
                      HotelNode hotelNode,
                      ResearchNode researchNode,
                      WeatherNode weatherNode,
                      @Qualifier("travelParallelExecutor") Executor executor) {
        this.flightNode = flightNode;
        this.hotelNode = hotelNode;
        this.researchNode = researchNode;
        this.weatherNode = weatherNode;
        this.executor = executor;
    }

    @Override
    public Map<String, Object> apply(TravelState state) {
        List<String> selected = SpecialistRouter.plannedSpecialists(state);
        GraphExecutionLogger.parallelFanOut(state);
        logSelection(state, selected);

        if (selected.isEmpty()) {
            return TravelState.trace(TravelGraphNodes.FAN_OUT, "skip", "no specialists requested");
        }

        List<CompletableFuture<Map<String, Object>>> futures = new ArrayList<>();
        if (state.runFlights()) {
            futures.add(runAsync(TravelGraphNodes.FLIGHT, state, () -> applyNode(flightNode, state)));
        }
        if (state.runHotels()) {
            futures.add(runAsync(TravelGraphNodes.HOTEL, state, () -> applyNode(hotelNode, state)));
        }
        if (state.runResearch()) {
            futures.add(runAsync(TravelGraphNodes.RESEARCH, state, () -> applyNode(researchNode, state)));
        }
        if (state.runWeather()) {
            futures.add(runAsync(TravelGraphNodes.WEATHER, state, () -> applyNode(weatherNode, state)));
        }

        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
        Map<String, Object> merged = new LinkedHashMap<>();
        List<Object> pipeline = new ArrayList<>();
        List<Object> provenance = new ArrayList<>();
        for (CompletableFuture<Map<String, Object>> future : futures) {
            Map<String, Object> updates = future.join();
            updates.forEach((key, value) -> {
                if (TravelState.PIPELINE.equals(key) && value instanceof List<?> list) {
                    pipeline.addAll(list);
                } else if (TravelState.PROVENANCE.equals(key) && value instanceof List<?> list) {
                    provenance.addAll(list);
                } else {
                    merged.put(key, value);
                }
            });
        }
        if (!pipeline.isEmpty()) {
            merged.put(TravelState.PIPELINE, pipeline);
        }
        if (!provenance.isEmpty()) {
            merged.put(TravelState.PROVENANCE, provenance);
        }
        merged.putAll(TravelState.trace(TravelGraphNodes.FAN_OUT, "ok", "executed=" + selected));
        GraphExecutionLogger.route(TravelGraphNodes.FAN_OUT, TravelGraphNodes.SUPERVISOR, state,
                "selectedSpecialists=" + selected);
        return merged;
    }

    private CompletableFuture<Map<String, Object>> runAsync(String node, TravelState state, java.util.function.Supplier<Map<String, Object>> action) {
        return CompletableFuture.supplyAsync(() -> {
            long started = System.nanoTime();
            GraphExecutionLogger.nodeStart(node, state);
            try {
                Map<String, Object> result = action.get();
                GraphExecutionLogger.nodeComplete(node, state, elapsedMs(started));
                return result;
            } catch (RuntimeException ex) {
                GraphExecutionLogger.nodeFailed(node, state, elapsedMs(started), ex.getMessage());
                throw ex;
            }
        }, executor);
    }

    private Map<String, Object> applyNode(NodeAction<TravelState> node, TravelState state) {
        try {
            return AgentStepEnricher.apply(node, state);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private long elapsedMs(long started) {
        return (System.nanoTime() - started) / 1_000_000L;
    }

   private void logSelection(TravelState state, List<String> specialists) {
    log.info(
            "[graph] fan-out selection threadId={} specialists={} needs=[flight={}, hotel={}, research={}, weather={}]",
            state.graphThreadId(),
            specialists,
            state.needsFlights(),
            state.needsHotels(),
            state.needsResearch(),
            state.needsWeather()
    );
}
}
