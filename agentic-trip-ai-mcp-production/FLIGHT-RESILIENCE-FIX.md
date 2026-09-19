# Flight Provider / Replan Resilience Fix

This build fixes the repeated flight-provider failure loop observed when AviationStack returns HTTP 429 or an open circuit.

## Changes

- Preserve specialist/provider failures from `ProductionExecutionNode` as structured `NodeFailureInfo` instead of collapsing them into a generic failed task.
- Treat provider HTTP 429, quota/rate-limit errors, and circuit-open responses as non-retryable for the current graph run.
- Stop the `evaluate -> replan -> execute` loop when the current specialist has a terminal provider failure.
- Prevent a later replan from re-adding a terminally failed flight task.
- Keep successful independent work (hotels, research, budget, etc.) and return a partial result.
- Fix retryable-state consistency between `McpFlightSearchClient` and the graph.
- Preserve explicit current-turn origin/destination over conversation-memory hydration.
- Make current-turn budget constraints override stale memory budget values.
- Make an explicit duration such as `7 days` override stale memory return dates.
- Apply LLM-provided normalization corrections when the model returns corrections but leaves `normalizedPrompt` unchanged.
- Reduce unnecessary secondary intent/memory LLM passes when the primary semantic intent already contains a coherent capability vector.

## Expected behavior

For a request such as `From Bengaluru to Tokyo for 7 days under ₹2 lakh...`, if the flight provider returns `PROVIDER_HTTP_429`:

1. Flights are marked unavailable/failed with the provider reason.
2. Hotels/research/budget that succeeded are retained.
3. The graph does not repeatedly search the same flight provider.
4. The final state is `PARTIAL` instead of spending several minutes in replan loops.
5. A later user request can explicitly request flights again, which starts a fresh capability attempt.
