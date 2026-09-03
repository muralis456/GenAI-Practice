# AgenticTripAI

**An Agentic AI Travel Planning & Orchestration Platform**

Graph-orchestrated multi-agent system: LangGraph4j decides *what runs next*; specialist agents live in `com.example.travel.agent`. Infrastructure stays in `com.example.travel.service`.

## Architecture branding

```
AgenticTripAI
 ├── Spring AI
 ├── LangGraph4j
 ├── Multi-Agent Orchestration
 ├── Tool Calling
 ├── Parallel Agents
 ├── Autonomous Replanning
 ├── Persistent Memory
 └── Human-in-the-Loop
```

## Workflow

```
USER → Intent (heuristic, LLM if confidence < 0.75) → Planner → Router
         → Airport (only if needsFlights)
         → fan_out (native LangGraph parallel fan-out)
              ├→ Flight
              ├→ Hotel
              ├→ Research
              └→ Weather  (each node no-ops when its needs* flag is false)
         → Supervisor (deterministic + LLM quality hint → proceed | retry)
         → Budget → Itinerary (or skip)
         → Hybrid Validator (deterministic + structured semantic JSON + plan quality score)
              FAIL / quality < 0.80 → Replanner (ReplanAction enum + selective needs*) → Router
              PASS → Final (Grounded Finalization)
         → interruptBefore(HITL)
              Approve → Complete → END
              Modify  → Modification Agent → Replan → Router
              Reject  → Cancel → END
```

LangGraph4j parallel fan-out uses `addParallelNodeExecutor(fan_out, …)` so Flight/Hotel/Research/Weather run concurrently and fan in at Supervisor.

### Grounded Finalization Pattern

Validated `TravelState` is rendered deterministically. The Final LLM writes **tips only**.

### Three memories

| Concern | Store | Purpose |
|--------|--------|---------|
| Conversation memory | `conversation_memory` | Chat turns |
| Graph checkpoints | `lg4jthread` / `lg4jcheckpoint` | Node, `TravelState`, HITL, time-travel |
| User preferences | `user_preference` | Airport, style, currency |

## Streaming

`POST /api/plan/start` → **202** `{threadId}` then `GET /api/plan/{threadId}/events` (SSE: `started` / `node` / `complete` / `failed`). Sync `POST /api/plan` still waits for the full graph.

`GET /api/plan/{threadId}/history` returns pipeline, plan quality, node failures, execution timeline, and LangGraph **state snapshots**.

## Guardrails

`max-retries`, `max-llm-calls`, `max-tavily-calls`, `max-iterations` (graph step cap).

## Tests

`AgentEvaluationTest`, `GraphTransitionTest`, plus `src/test/resources/evaluation/travel_cases.json`. `HitlPostgresCheckpointIT` needs PostgreSQL.

## Run

```bash
cd agentic-trip-ai
mvn spring-boot:run
```

UI: http://localhost:8081

**Maven coordinates:** `com.example:agentic-trip-ai`
