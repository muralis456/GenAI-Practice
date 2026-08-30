# Travel Agent (Spring AI + LangGraph4j)

**Graph-orchestrated multi-agent system** (not a fully autonomous swarm): the graph decides *what runs next*; specialists live in `com.example.travel.agent`. Infrastructure stays in `com.example.travel.service`.

## Architecture

```
USER → Intent (heuristic, LLM if confidence < 0.75) → Planner → Router
         → Airport (only if needsFlights)
         → Specialists (parallel: only Flight / Hotel / Research / Weather that Intent selected)
         → Supervisor (proceed | replan)
         → Budget → Itinerary (or skip)
         → Hybrid Validator (deterministic + semantic)
              FAIL → Replanner → Router
              PASS → Final (Grounded Finalization)
         → interruptBefore(HITL)
              Approve → Complete → END
              Modify  → Modification Agent → Replan → Router
              Reject  → Cancel → END
```

LangGraph4j 1.8 Command is **single-destination**, so a 4-way parallel fan-in cannot omit unused incoming edges. Unused specialists are therefore **never scheduled**: Router skips Airport/Specialists when they are not needed, and Specialists runs only the selected agents in parallel.

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

`GET /api/plan/{threadId}/history` returns pipeline + LangGraph **state snapshots** (time-travel inspection).

## Guardrails

`max-retries`, `max-llm-calls`, `max-tavily-calls`, `max-iterations` (graph step cap).

## Tests

`AgentEvaluationTest` plus `src/test/resources/evaluation/travel_cases.json`. `HitlPostgresCheckpointIT` needs PostgreSQL.

## Run

```bash
mvn spring-boot:run
```

UI: http://localhost:8081
