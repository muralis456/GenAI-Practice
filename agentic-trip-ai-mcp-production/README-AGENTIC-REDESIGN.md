# AgenticTripAI — Production Orchestration

## Core lifecycle

`USER -> INTENT -> PLAN -> EXECUTE(wave)* -> EVALUATE -> REPLAN -> EXECUTE(wave)* -> ... -> FINAL`

The graph is a deterministic lifecycle engine. The LLM is responsible for semantic reasoning; Java owns execution safety and state integrity.

### Intent
Understand the current user request, goal, constraints and requested capabilities. Intent does not choose graph routes.

### Plan
Create a dependency-aware `AgentPlan` containing a goal, observable success criteria and allow-listed tasks. Java rejects malformed dependencies and cycles.

### Execute
`ProductionExecutionNode` runs exactly one dependency wave. Independent specialists run in parallel. The graph loops back to `execute` only when another ready wave exists. This ensures every later wave sees persisted outputs from earlier waves.

### Evaluate
The evaluator checks actual evidence, not merely node execution. Deterministic checks are authoritative for hard outcomes such as usable flights, hotel results, budget estimates, weather data and itinerary presence. LLM evaluation can add semantic reasoning but cannot upgrade an objectively incomplete result to `ACHIEVED`.

### Replan
When the goal is not achieved and recovery is possible, the replanner creates a new plan from the failure evidence. Successful work is reused and only changed work plus downstream dependents are scheduled.

## Production reliability rules

- Technical failure and goal failure are separate concepts.
- Technical retries belong to the execution boundary; strategic recovery belongs to evaluation/replanning.
- A plan cannot contain unknown task ids or dependency cycles.
- A dependency wave is persisted before downstream work executes.
- Empty/default result objects do not count as successful evidence.
- Budget overflow is true only when a real budget estimate exists and an explicit budget ceiling is present.
- Replanning is bounded by `travel.graph.max-retries` and graph recursion limits.
- PostgreSQL checkpointing remains the persistence boundary.
- MCP/provider calls stay behind specialist/tool abstractions.

## Graph

```text
START
  |
 INTENT
  |
 PLAN
  |
 EXECUTE <----------------------- REPLAN
  |                                  ^
  | ready work?                      |
  +---- yes ----> EXECUTE            |
  |                                  |
  +---- no -----> EVALUATE ----------+
                    |
          +---------+---------+
          |         |         |
       ACHIEVED  RECOVERABLE  NEEDS_USER/FINAL
          |         |
         FINAL    REPLAN
```

The executor itself does not route based on `RUN_*` or `NEEDS_*` flags. The canonical source of execution is `AgentPlan` task state and dependencies.
