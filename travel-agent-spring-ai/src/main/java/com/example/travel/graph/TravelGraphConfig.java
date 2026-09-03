package com.example.travel.graph;

import com.example.travel.graph.node.AirportResolverNode;
import com.example.travel.graph.node.BudgetNode;
import com.example.travel.graph.node.CancelNode;
import com.example.travel.graph.node.CompleteNode;
import com.example.travel.graph.node.FanOutNode;
import com.example.travel.graph.node.FinalNode;
import com.example.travel.graph.node.FlightNode;
import com.example.travel.graph.node.HitlNode;
import com.example.travel.graph.node.HotelNode;
import com.example.travel.graph.node.IntentNode;
import com.example.travel.graph.node.ItineraryNode;
import com.example.travel.graph.node.PlannerNode;
import com.example.travel.graph.node.ReplanNode;
import com.example.travel.graph.node.ResearchNode;
import com.example.travel.graph.node.RouterNode;
import com.example.travel.graph.node.SupervisorNode;
import com.example.travel.graph.node.ValidatorNode;
import com.example.travel.graph.node.WeatherNode;
import com.example.travel.service.GraphRunContext;
import com.example.travel.service.ModelRoutingContext;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.action.Command;
import org.bsc.langgraph4j.checkpoint.PostgresSaver;
import org.bsc.langgraph4j.serializer.std.ObjectStreamStateSerializer;
import org.bsc.langgraph4j.utils.EdgeMappings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncCommandAction.command_async;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

@Configuration
public class TravelGraphConfig {

    private final GraphRunContext graphRunContext;

    public TravelGraphConfig(GraphRunContext graphRunContext) {
        this.graphRunContext = graphRunContext;
    }

    @Bean(destroyMethod = "shutdown")
    public ExecutorService travelParallelExecutor() {
        AtomicInteger index = new AtomicInteger();
        return Executors.newFixedThreadPool(4, runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName("travel-agent-parallel-" + index.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
    }

    @Bean(destroyMethod = "shutdown")
    public ExecutorService travelPlanExecutor() {
        AtomicInteger index = new AtomicInteger();
        return Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName("travel-plan-" + index.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
    }

    @Bean
    public ObjectStreamStateSerializer<TravelState> travelStateSerializer() {
        return new ObjectStreamStateSerializer<>(TravelState::new);
    }

    @Bean
    public PostgresSaver travelCheckpointSaver(DataSource dataSource,
                                               ObjectStreamStateSerializer<TravelState> travelStateSerializer)
            throws SQLException {
        return PostgresSaver.builder()
                .datasource(dataSource)
                .stateSerializer(travelStateSerializer)
                .createTables(true)
                .build();
    }

    @Bean
    public StateGraph<TravelState> travelStateGraph(IntentNode intentNode,
                                                     PlannerNode plannerNode,
                                                     RouterNode routerNode,
                                                     AirportResolverNode airportResolverNode,
                                                     FanOutNode fanOutNode,
                                                     FlightNode flightNode,
                                                     HotelNode hotelNode,
                                                     ResearchNode researchNode,
                                                     WeatherNode weatherNode,
                                                     SupervisorNode supervisorNode,
                                                     BudgetNode budgetNode,
                                                     ItineraryNode itineraryNode,
                                                     ValidatorNode validatorNode,
                                                     ReplanNode replanNode,
                                                     FinalNode finalNode,
                                                     HitlNode hitlNode,
                                                     CompleteNode completeNode,
                                                     CancelNode cancelNode,
                                                     ObjectStreamStateSerializer<TravelState> travelStateSerializer)
            throws GraphStateException {
        return new StateGraph<>(TravelState.SCHEMA, travelStateSerializer)
                .addNode(TravelGraphNodes.INTENT, async(intentNode))
                .addNode(TravelGraphNodes.PLANNER, async(plannerNode))
                .addNode(TravelGraphNodes.ROUTER, command_async((state, config) -> {
                    Map<String, Object> updates = enrich(routerNode, state);
                    return new Command(SpecialistRouter.afterPlanner(state), updates);
                }), Map.of(
                        TravelGraphNodes.AIRPORT, TravelGraphNodes.AIRPORT,
                        TravelGraphNodes.FAN_OUT, TravelGraphNodes.FAN_OUT,
                        TravelGraphNodes.SUPERVISOR, TravelGraphNodes.SUPERVISOR))
                .addNode(TravelGraphNodes.AIRPORT, async(airportResolverNode))
                .addNode(TravelGraphNodes.FAN_OUT, async(fanOutNode))
                .addNode(TravelGraphNodes.FLIGHT, async(flightNode))
                .addNode(TravelGraphNodes.HOTEL, async(hotelNode))
                .addNode(TravelGraphNodes.RESEARCH, async(researchNode))
                .addNode(TravelGraphNodes.WEATHER, async(weatherNode))
                .addNode(TravelGraphNodes.SUPERVISOR, async(supervisorNode))
                .addNode(TravelGraphNodes.BUDGET, async(budgetNode))
                .addNode(TravelGraphNodes.ITINERARY, async(itineraryNode))
                .addNode(TravelGraphNodes.VALIDATOR, async(validatorNode))
                .addNode(TravelGraphNodes.REPLAN, async(replanNode))
                .addNode(TravelGraphNodes.FINAL, async(finalNode))
                .addNode(TravelGraphNodes.HITL, async(hitlNode))
                .addNode(TravelGraphNodes.COMPLETE, async(completeNode))
                .addNode(TravelGraphNodes.CANCEL, async(cancelNode))
                .addEdge(START, TravelGraphNodes.INTENT)
                .addEdge(TravelGraphNodes.INTENT, TravelGraphNodes.PLANNER)
                .addEdge(TravelGraphNodes.PLANNER, TravelGraphNodes.ROUTER)
                .addConditionalEdges(TravelGraphNodes.AIRPORT,
                        edge_async(SpecialistRouter::afterAirport),
                        EdgeMappings.builder()
                                .to(TravelGraphNodes.FAN_OUT, TravelGraphNodes.FAN_OUT)
                                .to(TravelGraphNodes.SUPERVISOR, TravelGraphNodes.SUPERVISOR)
                                .build())
                .addEdge(TravelGraphNodes.FAN_OUT, TravelGraphNodes.FLIGHT)
                .addEdge(TravelGraphNodes.FAN_OUT, TravelGraphNodes.HOTEL)
                .addEdge(TravelGraphNodes.FAN_OUT, TravelGraphNodes.RESEARCH)
                .addEdge(TravelGraphNodes.FAN_OUT, TravelGraphNodes.WEATHER)
                .addEdge(TravelGraphNodes.FLIGHT, TravelGraphNodes.SUPERVISOR)
                .addEdge(TravelGraphNodes.HOTEL, TravelGraphNodes.SUPERVISOR)
                .addEdge(TravelGraphNodes.RESEARCH, TravelGraphNodes.SUPERVISOR)
                .addEdge(TravelGraphNodes.WEATHER, TravelGraphNodes.SUPERVISOR)
                .addConditionalEdges(TravelGraphNodes.SUPERVISOR,
                        edge_async(SpecialistRouter::afterSupervisor),
                        EdgeMappings.builder()
                                .to(TravelGraphNodes.REPLAN, TravelGraphNodes.REPLAN)
                                .to(TravelGraphNodes.BUDGET, TravelGraphNodes.BUDGET)
                                .to(TravelGraphNodes.ITINERARY, TravelGraphNodes.ITINERARY)
                                .to(TravelGraphNodes.VALIDATOR, TravelGraphNodes.VALIDATOR)
                                .build())
                .addConditionalEdges(TravelGraphNodes.BUDGET,
                        edge_async(state -> {
                            if (state.shouldReplanForBudget()) {
                                return TravelGraphNodes.ROUTE_OVER;
                            }
                            if (state.needsItinerary()) {
                                return TravelGraphNodes.ROUTE_UNDER;
                            }
                            return TravelGraphNodes.ROUTE_SKIP_ITINERARY;
                        }),
                        EdgeMappings.builder()
                                .to(TravelGraphNodes.ITINERARY, TravelGraphNodes.ROUTE_UNDER)
                                .to(TravelGraphNodes.REPLAN, TravelGraphNodes.ROUTE_OVER)
                                .to(TravelGraphNodes.VALIDATOR, TravelGraphNodes.ROUTE_SKIP_ITINERARY)
                                .build())
                .addEdge(TravelGraphNodes.ITINERARY, TravelGraphNodes.VALIDATOR)
                .addConditionalEdges(TravelGraphNodes.VALIDATOR,
                        edge_async(state -> state.shouldReplan()
                                ? TravelGraphNodes.ROUTE_INVALID : TravelGraphNodes.ROUTE_VALID),
                        EdgeMappings.builder()
                                .to(TravelGraphNodes.FINAL, TravelGraphNodes.ROUTE_VALID)
                                .to(TravelGraphNodes.REPLAN, TravelGraphNodes.ROUTE_INVALID)
                                .build())
                .addEdge(TravelGraphNodes.REPLAN, TravelGraphNodes.ROUTER)
                .addEdge(TravelGraphNodes.FINAL, TravelGraphNodes.HITL)
                .addConditionalEdges(TravelGraphNodes.HITL,
                        edge_async(state -> {
                            String decision = state.hitlDecision() == null ? "" : state.hitlDecision().toLowerCase();
                            if ("modify".equals(decision)) {
                                return TravelGraphNodes.ROUTE_MODIFY;
                            }
                            if ("reject".equals(decision)) {
                                return TravelGraphNodes.ROUTE_REJECT;
                            }
                            return TravelGraphNodes.ROUTE_APPROVE;
                        }),
                        EdgeMappings.builder()
                                .to(TravelGraphNodes.COMPLETE, TravelGraphNodes.ROUTE_APPROVE)
                                .to(TravelGraphNodes.REPLAN, TravelGraphNodes.ROUTE_MODIFY)
                                .to(TravelGraphNodes.CANCEL, TravelGraphNodes.ROUTE_REJECT)
                                .build())
                .addEdge(TravelGraphNodes.COMPLETE, END)
                .addEdge(TravelGraphNodes.CANCEL, END);
    }

    @Bean
    public CompiledGraph<TravelState> travelCompiledGraph(StateGraph<TravelState> travelStateGraph,
                                                          PostgresSaver travelCheckpointSaver,
                                                          @Value("${travel.graph.max-iterations:25}") int maxIterations)
            throws GraphStateException {
        return travelStateGraph.compile(CompileConfig.builder()
                .checkpointSaver(travelCheckpointSaver)
                .recursionLimit(maxIterations)
                .interruptBefore(TravelGraphNodes.HITL)
                .releaseThread(false)
                .build());
    }

    @Bean
    public RunnableConfig travelRunnableConfig(Executor travelParallelExecutor) {
        return RunnableConfig.builder()
                .addParallelNodeExecutor(TravelGraphNodes.FAN_OUT, travelParallelExecutor)
                .build();
    }

    private AsyncNodeAction<TravelState> async(org.bsc.langgraph4j.action.NodeAction<TravelState> node) {
        return node_async(state -> {
            String threadId = state.graphThreadId();
            if (!TravelState.isBlank(threadId)) {
                graphRunContext.attach(threadId);
            } else {
                ModelRoutingContext.set(state.modelPolicy());
            }
            try {
                return enrich(node, state);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            } finally {
                if (!TravelState.isBlank(threadId)) {
                    graphRunContext.detach();
                } else {
                    com.example.travel.service.ModelRoutingContext.clear();
                }
            }
        });
    }

    private Map<String, Object> enrich(org.bsc.langgraph4j.action.NodeAction<TravelState> node, TravelState state)
            throws Exception {
        return AgentStepEnricher.apply(node, state);
    }
}
