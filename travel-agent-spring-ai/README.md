# Travel Agent (Spring AI + LangGraph4j)

**Graph-orchestrated multi-agent system** (not a fully autonomous swarm): the graph decides *what runs next*; specialist agents use scoped tools; Airport, Budget, Currency, and the deterministic Validator stay rule-based.

## Architecture

```
USER → Intent → Planner → Airport
         → Flight ∥ Hotel ∥ Research (nodes skip when Intent says they are not needed)
         → Budget → Itinerary (or skip)
         → Deterministic Validator + Semantic LLM judge
         → Final (facts from TravelState + LLM tips only)
         → interruptBefore(HITL)
              Approve → Complete → END
              Modify  → Replan (LLM strategy) → agents…
```

### Design principle: Final never invents facts

Validated `TravelState` is rendered deterministically. The Final LLM writes **tips only**.

### Three memories (do not conflate)

| Concern | Store | Purpose |
|--------|--------|---------|
| Conversation memory | `conversation_memory` | What the user asked / assistant replied |
| Graph checkpoints | `lg4jthread` / `lg4jcheckpoint` | Node, `TravelState`, HITL pause/resume |
| User preferences | `user_preference` | Preferred airport, style, currency |

HITL is LangGraph **interrupt/resume** on the same `threadId` via `PostgresSaver` — not an in-memory map.

## Dynamic routing

Intent (heuristics, LLM-optional) sets `needsFlights|Hotels|Research|Weather|Budget|Itinerary`. Nodes **skip** unused APIs (no AviationStack for sightseeing-only queries). Budget can skip itinerary via a conditional edge.

## Replan

Replanner LLM returns `ReplanStrategy` JSON (`actions`, `priority`). Fallback is the previous cost-factor rule.

## Tools

Scoped access (see `ToolRegistry`): Flight gets airport+AviationStack; Research gets Tavily+weather; 403/422 are **not** retried; timeouts/429 are.

## Run

```bash
mvn spring-boot:run
```

UI: http://localhost:8081

## Tests

- `HitlPostgresCheckpointIT` — interrupt/resume after simulated restart
- `AgentEvaluationTest` — intent routing, validator, tool-error classification
