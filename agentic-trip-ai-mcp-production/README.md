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
USER → Semantic Intent LLM → schema validation → Planner → Router → Planner → Router
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


## MCP LLM Tool Selection Logging

The client logs the dynamic MCP tool-selection path so it is easy to observe:
- MCP tool lookup requested
- LLM-selected/resolved MCP tool
- MCP tool invocation started
- MCP invocation failures

Use `DEBUG` for detailed tool-selection diagnostics and `INFO` for the selected tool and invocation lifecycle.

## RAG Evaluation & Groundedness

The project now includes a deterministic RAG evaluation layer that can run without an LLM judge:

- Retrieval context relevance
- Expected keyword coverage
- Expected source recall
- Overall retrieval evidence score
- Answer-vs-context groundedness screening metric

Endpoints:

```text
POST /api/rag/evaluate
POST /api/rag/groundedness
```

`/api/rag/evaluate` runs the built-in retrieval benchmark against the current pgvector index. The benchmark now generates an evidence-only answer and uses an LLM judge for groundedness. `/api/rag/groundedness` accepts `{ "answer": "...", "context": "..." }` and returns a lexical screening score. `/api/rag/judge` accepts `{ "userRequest": "...", "answer": "...", "context": "..." }` and returns LLM-as-a-judge groundedness, coverage, unsupported-claim rate, pass/fail and reason. During the graph, `ValidatorNode` judges the generated itinerary against RAG evidence; a failed judge adds a validation error and sends the graph through the existing replan loop.

The groundedness score is a guardrail/smoke metric, not a substitute for an LLM-as-judge evaluation. For production evaluation, add a second judge using a stronger model and compare it with these deterministic metrics.

### Persistent trip-memory retrieval

Requests that semantically ask to recall/reopen a previous trip are classified by the Intent Agent with `needsHistory=true`. The graph routes those requests to the `history` node, which reads the newest saved structured trip from PostgreSQL `trip_history` and falls back to structured `conversation_memory` rows created by older versions. It does not start a new Planner/specialist run and does not use the browser's localStorage as the source of truth.

## P0 production hardening

See `P0-PRODUCTION-SECURITY.md` for the authentication, authorization, Flyway, bounded execution, rate limiting, and durable SSE changes included in this build.


### Security configuration
The application uses the modern Spring Security request-matcher DSL and does not depend on `AntPathRequestMatcher`. See `P0-PRODUCTION-SECURITY.md` for the authentication and production-hardening details.
