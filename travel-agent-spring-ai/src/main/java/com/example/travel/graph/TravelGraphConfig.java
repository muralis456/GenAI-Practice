package com.example.travel.graph;

import com.example.travel.graph.node.AirportResolverNode;
import com.example.travel.graph.node.BudgetNode;
import com.example.travel.graph.node.FinalNode;
import com.example.travel.graph.node.FlightNode;
import com.example.travel.graph.node.HotelNode;
import com.example.travel.graph.node.ItineraryNode;
import com.example.travel.graph.node.PlannerNode;
import com.example.travel.graph.node.ReplanNode;
import com.example.travel.graph.node.ResearchNode;
import com.example.travel.graph.node.ValidatorNode;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.bsc.langgraph4j.utils.EdgeMappings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

@Configuration
public class TravelGraphConfig {

    @Bean(destroyMethod = "shutdown")
    public ExecutorService travelParallelExecutor() {
        AtomicInteger index = new AtomicInteger();
        return Executors.newFixedThreadPool(3, runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName("travel-agent-parallel-" + index.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
    }

    @Bean
    public MemorySaver travelMemorySaver() {
        return new MemorySaver();
    }

    @Bean
    public StateGraph<TravelState> travelStateGraph(PlannerNode plannerNode,
                                                     AirportResolverNode airportResolverNode,
                                                     FlightNode flightNode,
                                                     ResearchNode researchNode,
                                                     HotelNode hotelNode,
                                                     BudgetNode budgetNode,
                                                     ItineraryNode itineraryNode,
                                                     ValidatorNode validatorNode,
                                                     ReplanNode replanNode,
                                                     FinalNode finalNode) throws GraphStateException {
        return new StateGraph<>(TravelState.SCHEMA, TravelState::new)
                .addNode(TravelGraphNodes.PLANNER, async(plannerNode))
                .addNode(TravelGraphNodes.AIRPORT, async(airportResolverNode))
                .addNode(TravelGraphNodes.FLIGHT, async(flightNode))
                .addNode(TravelGraphNodes.RESEARCH, async(researchNode))
                .addNode(TravelGraphNodes.HOTEL, async(hotelNode))
                .addNode(TravelGraphNodes.BUDGET, async(budgetNode))
                .addNode(TravelGraphNodes.ITINERARY, async(itineraryNode))
                .addNode(TravelGraphNodes.VALIDATOR, async(validatorNode))
                .addNode(TravelGraphNodes.REPLAN, async(replanNode))
                .addNode(TravelGraphNodes.FINAL, async(finalNode))
                .addEdge(START, TravelGraphNodes.PLANNER)
                .addEdge(TravelGraphNodes.PLANNER, TravelGraphNodes.AIRPORT)
                .addEdge(TravelGraphNodes.AIRPORT, TravelGraphNodes.FLIGHT)
                .addEdge(TravelGraphNodes.AIRPORT, TravelGraphNodes.RESEARCH)
                .addEdge(TravelGraphNodes.AIRPORT, TravelGraphNodes.HOTEL)
                .addEdge(TravelGraphNodes.FLIGHT, TravelGraphNodes.BUDGET)
                .addEdge(TravelGraphNodes.RESEARCH, TravelGraphNodes.BUDGET)
                .addEdge(TravelGraphNodes.HOTEL, TravelGraphNodes.BUDGET)
                .addConditionalEdges(TravelGraphNodes.BUDGET,
                        edge_async(state -> state.shouldReplanForBudget()
                                ? TravelGraphNodes.ROUTE_OVER : TravelGraphNodes.ROUTE_UNDER),
                        EdgeMappings.builder()
                                .to(TravelGraphNodes.ITINERARY, TravelGraphNodes.ROUTE_UNDER)
                                .to(TravelGraphNodes.REPLAN, TravelGraphNodes.ROUTE_OVER)
                                .build())
                .addEdge(TravelGraphNodes.ITINERARY, TravelGraphNodes.VALIDATOR)
                .addConditionalEdges(TravelGraphNodes.VALIDATOR,
                        edge_async(state -> state.shouldReplan()
                                ? TravelGraphNodes.ROUTE_INVALID : TravelGraphNodes.ROUTE_VALID),
                        EdgeMappings.builder()
                                .to(TravelGraphNodes.FINAL, TravelGraphNodes.ROUTE_VALID)
                                .to(TravelGraphNodes.REPLAN, TravelGraphNodes.ROUTE_INVALID)
                                .build())
                .addEdge(TravelGraphNodes.REPLAN, TravelGraphNodes.FLIGHT)
                .addEdge(TravelGraphNodes.REPLAN, TravelGraphNodes.RESEARCH)
                .addEdge(TravelGraphNodes.REPLAN, TravelGraphNodes.HOTEL)
                .addEdge(TravelGraphNodes.FINAL, END);
    }

    @Bean
    public CompiledGraph<TravelState> travelCompiledGraph(StateGraph<TravelState> travelStateGraph,
                                                         MemorySaver travelMemorySaver) throws GraphStateException {
        return travelStateGraph.compile(CompileConfig.builder()
                .checkpointSaver(travelMemorySaver)
                .build());
    }

    @Bean
    public RunnableConfig travelRunnableConfig(Executor travelParallelExecutor) {
        return RunnableConfig.builder()
                .addParallelNodeExecutor(TravelGraphNodes.AIRPORT, travelParallelExecutor)
                .addParallelNodeExecutor(TravelGraphNodes.REPLAN, travelParallelExecutor)
                .build();
    }

    private AsyncNodeAction<TravelState> async(org.bsc.langgraph4j.action.NodeAction<TravelState> node) {
        return node_async(state -> {
            long started = System.currentTimeMillis();
            java.util.Map<String, Object> result = new java.util.LinkedHashMap<>(node.apply(state));
            long durationMs = System.currentTimeMillis() - started;
            Object pipeline = result.get(TravelState.PIPELINE);
            if (pipeline instanceof java.util.List<?> steps) {
                for (Object step : steps) {
                    if (step instanceof com.example.travel.model.AgentStep agentStep) {
                        agentStep.setDurationMs(durationMs);
                    }
                }
            }
            return result;
        });
    }
}
