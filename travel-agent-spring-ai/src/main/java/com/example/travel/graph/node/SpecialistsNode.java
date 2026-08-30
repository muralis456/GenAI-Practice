package com.example.travel.graph.node;

import com.example.travel.graph.AgentStepEnricher;
import com.example.travel.graph.SpecialistRouter;
import com.example.travel.graph.TravelGraphNodes;
import com.example.travel.graph.TravelState;
import com.example.travel.model.AgentStep;
import com.example.travel.model.ProvenanceEvent;
import com.example.travel.service.AgentExecutionBudget;
import com.example.travel.service.LlmCallContext;
import com.example.travel.service.ModelRoutingContext;
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
 * Runs only the specialists Intent selected, in parallel. Unused APIs are not invoked.
 * Worker threads re-attach the graph run budget and model policy (pooled threads do not inherit them).
 */
@Component
public class SpecialistsNode implements NodeAction<TravelState> {

    private final FlightNode flightNode;
    private final HotelNode hotelNode;
    private final ResearchNode researchNode;
    private final WeatherNode weatherNode;
    private final Executor travelParallelExecutor;
    private final AgentExecutionBudget executionBudget;

    public SpecialistsNode(FlightNode flightNode,
                           HotelNode hotelNode,
                           ResearchNode researchNode,
                           WeatherNode weatherNode,
                           @Qualifier("travelParallelExecutor") Executor travelParallelExecutor,
                           AgentExecutionBudget executionBudget) {
        this.flightNode = flightNode;
        this.hotelNode = hotelNode;
        this.researchNode = researchNode;
        this.weatherNode = weatherNode;
        this.travelParallelExecutor = travelParallelExecutor;
        this.executionBudget = executionBudget;
    }

    @Override
    public Map<String, Object> apply(TravelState state) {
        List<String> ran = SpecialistRouter.plannedSpecialists(state);
        if (ran.isEmpty()) {
            return TravelState.trace(TravelGraphNodes.SPECIALISTS, "skip", "no specialists requested");
        }

        AgentExecutionBudget.Counters budget = executionBudget.capture();
        if (budget == null) {
            executionBudget.begin();
            budget = executionBudget.capture();
        }
        String policy = ModelRoutingContext.get();

        List<CompletableFuture<Map<String, Object>>> tasks = new ArrayList<>();
        if (state.needsFlights()) {
            tasks.add(run(flightNode, state, budget, policy));
        }
        if (state.needsHotels()) {
            tasks.add(run(hotelNode, state, budget, policy));
        }
        if (state.needsResearch()) {
            tasks.add(run(researchNode, state, budget, policy));
        }
        if (state.needsWeather()) {
            tasks.add(run(weatherNode, state, budget, policy));
        }

        Map<String, Object> merged = new LinkedHashMap<>();
        List<AgentStep> steps = new ArrayList<>();
        List<ProvenanceEvent> events = new ArrayList<>();
        for (CompletableFuture<Map<String, Object>> task : tasks) {
            merge(merged, task.join(), steps, events);
        }
        steps.add(new AgentStep(TravelGraphNodes.SPECIALISTS, "ok", "ran " + ran));
        merged.put(TravelState.PIPELINE, steps);
        if (!events.isEmpty()) {
            merged.put(TravelState.PROVENANCE, events);
        }
        return merged;
    }

    private CompletableFuture<Map<String, Object>> run(NodeAction<TravelState> node,
                                                       TravelState state,
                                                       AgentExecutionBudget.Counters budget,
                                                       String policy) {
        return CompletableFuture.supplyAsync(() -> {
            executionBudget.use(budget);
            ModelRoutingContext.set(policy);
            long started = System.currentTimeMillis();
            try {
                Map<String, Object> result = node.apply(state);
                AgentStepEnricher.attach(result, state, System.currentTimeMillis() - started);
                return result;
            } catch (Exception ex) {
                LlmCallContext.consume();
                Map<String, Object> failed = new LinkedHashMap<>();
                failed.putAll(TravelState.trace("specialist", "fail",
                        ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()));
                return failed;
            } finally {
                ModelRoutingContext.clear();
                executionBudget.end();
            }
        }, travelParallelExecutor);
    }

    private void merge(Map<String, Object> into, Map<String, Object> part,
                       List<AgentStep> steps, List<ProvenanceEvent> events) {
        for (Map.Entry<String, Object> entry : part.entrySet()) {
            if (TravelState.PIPELINE.equals(entry.getKey()) && entry.getValue() instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof AgentStep step) {
                        steps.add(step);
                    }
                }
                continue;
            }
            if (TravelState.PROVENANCE.equals(entry.getKey()) && entry.getValue() instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof ProvenanceEvent event) {
                        events.add(event);
                    }
                }
                continue;
            }
            into.put(entry.getKey(), entry.getValue());
        }
    }
}
