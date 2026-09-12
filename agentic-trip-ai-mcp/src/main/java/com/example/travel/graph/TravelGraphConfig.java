package com.example.travel.graph;

import com.example.travel.graph.node.AirportResolverNode;
import com.example.travel.graph.node.BudgetNode;
import com.example.travel.graph.node.CancelNode;
import com.example.travel.graph.node.CompleteNode;
import com.example.travel.graph.node.FanOutNode;
import com.example.travel.graph.node.FinalizationNode;
import com.example.travel.graph.node.FlightNode;
import com.example.travel.graph.node.HitlNode;
import com.example.travel.graph.node.HotelNode;
import com.example.travel.graph.node.IntentNode;
import com.example.travel.graph.node.ItineraryNode;
import com.example.travel.graph.node.PlannerNode;
import com.example.travel.graph.node.ReplanNode;
import com.example.travel.graph.node.RagNode;
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
import org.bsc.langgraph4j.action.NodeAction;
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
            thread.setName("agentic-trip-ai-parallel-" + index.incrementAndGet());
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
            RagNode ragNode,
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
            FinalizationNode finalizationNode,
            HitlNode hitlNode,
            CompleteNode completeNode,
            CancelNode cancelNode,
            ObjectStreamStateSerializer<TravelState> travelStateSerializer)
            throws GraphStateException {
        return new StateGraph<>(TravelState.SCHEMA, travelStateSerializer)
                .addNode(TravelGraphNodes.INTENT, async(TravelGraphNodes.INTENT, intentNode))
                .addNode(TravelGraphNodes.PLANNER, async(TravelGraphNodes.PLANNER, plannerNode))
                .addNode(TravelGraphNodes.RAG, async(TravelGraphNodes.RAG, ragNode))
                .addNode(TravelGraphNodes.ROUTER, command_async((state, config) -> {
                    GraphExecutionLogger.nodeStart(TravelGraphNodes.ROUTER, state);
                    long started = System.nanoTime();
                    try {
                        Map<String, Object> updates = enrich(routerNode, state);
                        String next = SpecialistRouter.afterPlanner(state);
                        GraphExecutionLogger.nodeComplete(TravelGraphNodes.ROUTER, state, elapsedMs(started));
                        return new Command(next, updates);
                    } catch (Exception ex) {
                        GraphExecutionLogger.nodeFailed(TravelGraphNodes.ROUTER, state, elapsedMs(started),
                                ex.getMessage());
                        throw new RuntimeException(ex);
                    }
                }), Map.of(
                        TravelGraphNodes.AIRPORT, TravelGraphNodes.AIRPORT,
                        TravelGraphNodes.FAN_OUT, TravelGraphNodes.FAN_OUT,
                        TravelGraphNodes.SUPERVISOR, TravelGraphNodes.SUPERVISOR,
                        TravelGraphNodes.FLIGHT, TravelGraphNodes.FLIGHT,
                        TravelGraphNodes.HOTEL, TravelGraphNodes.HOTEL,
                        TravelGraphNodes.RESEARCH, TravelGraphNodes.RESEARCH,
                        TravelGraphNodes.WEATHER, TravelGraphNodes.WEATHER))
                .addNode(TravelGraphNodes.AIRPORT, async(TravelGraphNodes.AIRPORT, airportResolverNode))
                .addNode(TravelGraphNodes.FAN_OUT, async(TravelGraphNodes.FAN_OUT, fanOutNode))
                .addNode(TravelGraphNodes.FLIGHT, async(TravelGraphNodes.FLIGHT, flightNode))
                .addNode(TravelGraphNodes.HOTEL, async(TravelGraphNodes.HOTEL, hotelNode))
                .addNode(TravelGraphNodes.RESEARCH, async(TravelGraphNodes.RESEARCH, researchNode))
                .addNode(TravelGraphNodes.WEATHER, async(TravelGraphNodes.WEATHER, weatherNode))
                .addNode(TravelGraphNodes.SUPERVISOR, async(TravelGraphNodes.SUPERVISOR, supervisorNode))
                .addNode(TravelGraphNodes.BUDGET, async(TravelGraphNodes.BUDGET, budgetNode))
                .addNode(TravelGraphNodes.ITINERARY, async(TravelGraphNodes.ITINERARY, itineraryNode))
                .addNode(TravelGraphNodes.VALIDATOR, async(TravelGraphNodes.VALIDATOR, validatorNode))
                .addNode(TravelGraphNodes.REPLAN, async(TravelGraphNodes.REPLAN, replanNode))
                .addNode(TravelGraphNodes.FINAL, async(TravelGraphNodes.FINAL, finalizationNode))
                .addNode(TravelGraphNodes.HITL, async(TravelGraphNodes.HITL, hitlNode))
                .addNode(TravelGraphNodes.COMPLETE, async(TravelGraphNodes.COMPLETE, completeNode))
                .addNode(TravelGraphNodes.CANCEL, async(TravelGraphNodes.CANCEL, cancelNode))
                .addEdge(START, TravelGraphNodes.INTENT)
                .addConditionalEdges(TravelGraphNodes.INTENT,
                        edge_async(state -> {
                            // Planner is only for requests that need trip-slot execution.
                            // Knowledge/research requests can resolve their destination
                            // through RAG and must never invent dates, budget or routes.
                            if (state.needsKnowledge() || state.needsResearch()) {
                                return TravelGraphNodes.RAG;
                            }
                            if (!hasAnyIntentCapability(state)) {
                                return TravelGraphNodes.FINAL;
                            }
                            return TravelGraphNodes.PLANNER;
                        }),
                        EdgeMappings.builder()
                                .to(TravelGraphNodes.RAG, TravelGraphNodes.RAG)
                                .to(TravelGraphNodes.PLANNER, TravelGraphNodes.PLANNER)
                                .to(TravelGraphNodes.FINAL, TravelGraphNodes.FINAL)
                                .build())
                .addConditionalEdges(TravelGraphNodes.PLANNER,
                        edge_async(state -> state.needsKnowledge() ? TravelGraphNodes.RAG : TravelGraphNodes.ROUTER),
                        EdgeMappings.builder()
                                .to(TravelGraphNodes.RAG, TravelGraphNodes.RAG)
                                .to(TravelGraphNodes.ROUTER, TravelGraphNodes.ROUTER)
                                .build())
                .addConditionalEdges(TravelGraphNodes.RAG,
                        edge_async(TravelGraphConfig::afterRag),
                        EdgeMappings.builder()
                                .to(TravelGraphNodes.ROUTER, TravelGraphNodes.ROUTER)
                                .to(TravelGraphNodes.RESEARCH, TravelGraphNodes.RESEARCH)
                                .to(TravelGraphNodes.VALIDATOR, TravelGraphNodes.VALIDATOR)
                                .build())
                .addConditionalEdges(TravelGraphNodes.AIRPORT,
                        edge_async(SpecialistRouter::afterAirport),
                        EdgeMappings.builder()
                                .to(TravelGraphNodes.FAN_OUT, TravelGraphNodes.FAN_OUT)
                                .to(TravelGraphNodes.FLIGHT, TravelGraphNodes.FLIGHT)
                                .to(TravelGraphNodes.HOTEL, TravelGraphNodes.HOTEL)
                                .to(TravelGraphNodes.RESEARCH, TravelGraphNodes.RESEARCH)
                                .to(TravelGraphNodes.WEATHER, TravelGraphNodes.WEATHER)
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
                            String route;
                            String reason;
                            if (state.shouldReplanForBudget()) {
                                route = TravelGraphNodes.ROUTE_OVER;
                                reason = "overBudget";
                            } else if (state.needsItinerary()) {
                                route = TravelGraphNodes.ROUTE_UNDER;
                                reason = "withinBudgetNeedsItinerary";
                            } else {
                                route = TravelGraphNodes.ROUTE_SKIP_ITINERARY;
                                reason = "withinBudgetSkipItinerary";
                            }
                            GraphExecutionLogger.route(TravelGraphNodes.BUDGET,
                                    route.equals(TravelGraphNodes.ROUTE_OVER) ? TravelGraphNodes.REPLAN
                                            : route.equals(TravelGraphNodes.ROUTE_UNDER) ? TravelGraphNodes.ITINERARY
                                                    : TravelGraphNodes.VALIDATOR,
                                    state, reason);
                            return route;
                        }),
                        EdgeMappings.builder()
                                .to(TravelGraphNodes.ITINERARY, TravelGraphNodes.ROUTE_UNDER)
                                .to(TravelGraphNodes.REPLAN, TravelGraphNodes.ROUTE_OVER)
                                .to(TravelGraphNodes.VALIDATOR, TravelGraphNodes.ROUTE_SKIP_ITINERARY)
                                .build())
                .addEdge(TravelGraphNodes.ITINERARY, TravelGraphNodes.VALIDATOR)
                .addConditionalEdges(TravelGraphNodes.VALIDATOR,
                        edge_async(state -> {
                            boolean replan = state.shouldReplan();
                            GraphExecutionLogger.route(TravelGraphNodes.VALIDATOR,
                                    replan ? TravelGraphNodes.REPLAN : TravelGraphNodes.FINAL,
                                    state,
                                    replan ? "validationOrQualityFailed" : "validationPassed");
                            return replan ? TravelGraphNodes.ROUTE_INVALID : TravelGraphNodes.ROUTE_VALID;
                        }),
                        EdgeMappings.builder()
                                .to(TravelGraphNodes.FINAL, TravelGraphNodes.ROUTE_VALID)
                                .to(TravelGraphNodes.REPLAN, TravelGraphNodes.ROUTE_INVALID)
                                .build())
                .addEdge(TravelGraphNodes.REPLAN, TravelGraphNodes.ROUTER)
                .addEdge(TravelGraphNodes.FINAL, TravelGraphNodes.HITL)
                .addConditionalEdges(TravelGraphNodes.HITL,
                        edge_async(state -> {
                            String decision = state.hitlDecision() == null ? "" : state.hitlDecision().toLowerCase();
                            String route;
                            if ("modify".equals(decision)) {
                                route = TravelGraphNodes.ROUTE_MODIFY;
                            } else if ("reject".equals(decision)) {
                                route = TravelGraphNodes.ROUTE_REJECT;
                            } else {
                                route = TravelGraphNodes.ROUTE_APPROVE;
                            }
                            GraphExecutionLogger.hitl(state, decision, state.awaitingApproval());
                            GraphExecutionLogger.route(TravelGraphNodes.HITL,
                                    "modify".equals(decision) ? TravelGraphNodes.REPLAN
                                            : "reject".equals(decision) ? TravelGraphNodes.CANCEL
                                                    : TravelGraphNodes.COMPLETE,
                                    state, "hitlDecision=" + decision);
                            return route;
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

    private static boolean hasAnyIntentCapability(TravelState state) {
        return state != null && (state.needsFlights()
                || state.needsHotels()
                || state.needsResearch()
                || state.needsWeather()
                || state.needsBudget()
                || state.needsItinerary()
                || state.needsKnowledge());
    }

    private static String afterRag(TravelState state) {
        if (state == null) return TravelGraphNodes.VALIDATOR;
        boolean tripExecution = state.runFlights() || state.runHotels() || state.runWeather()
                || state.runBudget() || state.runItinerary();
        if (tripExecution) return TravelGraphNodes.ROUTER;
        if (state.runResearch()) return TravelGraphNodes.RESEARCH;
        return TravelGraphNodes.VALIDATOR;
    }

    private AsyncNodeAction<TravelState> async(String nodeName,
            NodeAction<TravelState> node) {
        return node_async(state -> {
            GraphExecutionLogger.nodeStart(nodeName, state);
            long started = System.nanoTime();
            String threadId = state.graphThreadId();
            if (!TravelState.isBlank(threadId)) {
                graphRunContext.attach(threadId);
            } else {
                ModelRoutingContext.set(state.modelPolicy());
            }
            ModelRoutingContext.setComplexity(complexity(state));
            try {
                Map<String, Object> updates = enrich(node, state);
                GraphExecutionLogger.nodeComplete(nodeName, state, elapsedMs(started));
                return updates;
            } catch (Exception ex) {
                GraphExecutionLogger.nodeFailed(nodeName, state, elapsedMs(started), ex.getMessage());
                throw new RuntimeException(ex);
            } finally {
                if (!TravelState.isBlank(threadId)) {
                    graphRunContext.detach();
                } else {
                    ModelRoutingContext.clear();
                }
            }
        });
    }

    private static ModelRoutingContext.Complexity complexity(TravelState state) {
        if (state == null) return ModelRoutingContext.Complexity.SIMPLE;
        int capabilities = 0;
        if (state.needsFlights()) capabilities++;
        if (state.needsHotels()) capabilities++;
        if (state.needsResearch()) capabilities++;
        if (state.needsWeather()) capabilities++;
        if (state.needsBudget()) capabilities++;
        if (state.needsItinerary()) capabilities++;
        if (state.needsKnowledge()) capabilities++;
        if (capabilities >= 4) return ModelRoutingContext.Complexity.COMPLEX;
        if (capabilities >= 2) return ModelRoutingContext.Complexity.NORMAL;
        return ModelRoutingContext.Complexity.SIMPLE;
    }

    private static long elapsedMs(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000L;
    }

    private Map<String, Object> enrich(org.bsc.langgraph4j.action.NodeAction<TravelState> node, TravelState state)
            throws Exception {
        return AgentStepEnricher.apply(node, state);
    }
}
