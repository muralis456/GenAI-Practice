# RAG Fallback Resilience

## Behavior

RAG is now an independent knowledge path. Retrieval produces a grounded answer before downstream research/supervisor processing.

- RAG stores `ragAnswer` in graph state.
- Knowledge/research requests bypass the trip-slot Planner unless flight/hotel/weather/budget/itinerary execution is actually required.
- RAG destination resolution can populate the state destination when the request did not come through Planner.
- If the final answer LLM is unavailable because the graph LLM budget is exhausted, the grounded RAG context is returned as a safe fallback.
- RAG judge evaluates `ragAnswer` first.
- If the RAG judge LLM is unavailable, validation uses the deterministic RAG evidence score instead of triggering destructive replanning.
- Research-only paths go directly to validation instead of spending an unnecessary Supervisor LLM call.

## Example

`Bangalore trip precautions` should not create an invented route or budget. The graph is:

`INTENT -> RAG -> RESEARCH (if semantically requested) -> VALIDATOR -> FINAL`

If research fails, the previously generated grounded RAG answer remains available to Finalization.
