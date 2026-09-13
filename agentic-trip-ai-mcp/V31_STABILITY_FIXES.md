# V31 Stability Fixes

## Root causes addressed
- A scoped hotel price ceiling such as "best hotels in Bengaluru under 50k" was being interpreted as a separate overall Budget capability. Added semantic `budgetScope` to IntentPlan and clarified that specialist-scoped monetary constraints do not schedule Budget.
- Planner now carries `hotelBudget` separately and does not overwrite the overall trip budget for hotel-only requests.
- MCP hotel responses that contain an article/listicle are rejected as hotel entities. If structured hotel results are invalid/empty, the agent falls back to independent travel research and extracts only verified hotel properties.
- Hotel no-result outcomes are explicitly logged as `no_verified_results`; no fake hotel card is produced.
- Specialist empty-state UI is compact.

## Architecture principle
The semantic LLM decides capabilities. Java validates the structured decision and routes. Monetary scope is a semantic field, not a keyword-based intent rule.


## Regression examples covered by the semantic contract
- "best hotels in Bengaluru under 50k" -> hotels capability only; 50k is a hotel-scoped constraint.
- "flights under 20k" -> flights capability only; 20k is a flight-scoped constraint.
- "what will the whole trip cost" -> Budget capability with TRIP scope.
