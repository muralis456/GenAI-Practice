# V29 Stability Fixes

## Root cause from the 2026-09-13 flight log

A flight-only request was exhausting the 20-call graph LLM budget before the actual flight MCP call could complete. The Planner Agent exposed `AirportLookupTool` as a Spring AI tool and the model repeatedly attempted airport resolution. Each invocation crossed `McpAirportClient -> McpToolClient -> McpToolSelector`, and MCP tool selection itself consumed an LLM call. Once the graph budget reached 20/20, the selector received an empty model result and raised `MCP tool selector returned an empty response`.

## Changes

1. Planner no longer exposes airport lookup as a tool. Planner extracts current-turn trip slots only. Airport resolution remains a graph/data concern.
2. `AirportLookupTool` now resolves from the local airport database first, then static fallback mappings, then MCP only for an unresolved airport. Common BLR/BOM/etc. lookups therefore do not consume an LLM call.
3. `McpToolSelector` caches the selected tool per agent purpose + MCP catalog. Repeated identical infrastructure selections do not consume repeated LLM calls.
4. Invalid flight dates no longer fall back to `LocalDate.now()`.
5. No weather/flight/hotel-specific intent rule was added. Top-level semantic intent remains the source of current-turn capability decisions.

## Expected flight-only flow

Intent -> Planner -> Airport resolution -> Flight -> Supervisor -> Validator/Final.

For `what are flights available from Bangalore to Mumbai`, the planner should not call airport tools, BLR/BOM should normally resolve locally, and the flight MCP tool should be selected once and then invoked.
