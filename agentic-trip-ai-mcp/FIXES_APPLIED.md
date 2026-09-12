# AgenticTravelAI fixes applied

## Graph / Intent
- Specialist `needs*` and `run*` flags default to `false`.
- `IntentPlan.fullTrip()` now explicitly enables all capabilities for a real full-trip request.
- Knowledge-only questions are classified as `KNOWLEDGE_QUERY` and route directly to RAG.
- Research-only requests no longer implicitly enable weather.
- "Already booked flight" requests only enable the capabilities explicitly requested.
- Intent logs now show all capability decisions, strategy, priority and confidence.

## Selective specialist execution
- Removed unconditional `fan_out -> flight/hotel/research/weather` graph edges.
- `FanOutNode` now selects requested specialists at runtime and executes only those specialists concurrently.
- Specialist results are joined safely, including pipeline/provenance lists.
- Single-specialist requests continue to bypass fan-out.

## MCP
- Added capability guards so weather cannot select `search_travel_research`, airport cannot select a research tool, etc.
- Added required-argument validation before MCP invocation.
- MCP client logs tool catalog, selection mode, selected tool, argument keys, invocation, response and failures.
- MCP domain clients now log failures instead of silently swallowing them.

## RAG observability
Added logs for:
- RAG start/configuration
- retrieval decision
- multi-query generation
- vector retrieval counts
- PostgreSQL FTS retrieval counts
- RRF fusion counts/sources
- reranking before/after counts
- compressed context size
- evidence sufficiency evaluation
- final RAG metrics and duration
- RAG node result details

## RAG / pgvector
- Existing 768-dimensional `nomic-embed-text` configuration is preserved.
- No destructive vector-store changes are made by these fixes.

## Verification
- `IntentPlan` and `IntentClassifier` compile successfully with the available Java compiler.
- Deterministic intent checks verified:
  - Eiffel Tower history -> `KNOWLEDGE_QUERY`, RAG only
  - Paris attractions -> research only
  - BLR -> Paris flights -> flight only
  - Full Japan trip -> all full-trip capabilities
  - Already-booked flight + hotels/things to do -> hotels + research only
- Full Maven test suite was not run in this environment because the uploaded project requires Java 26 and Maven was not available in the execution environment.
