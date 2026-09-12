# Production Dynamic Intent Architecture

The production graph no longer uses keyword matching to choose the initial intent. The Intent Agent performs semantic, multi-intent classification with the configured LLM. Java applies only safety/normalization and graph routing.

## Decision flow

USER REQUEST -> SEMANTIC INTENT AGENT -> CAPABILITY PLAN -> GRAPH ROUTING

The semantic intent agent understands natural language noise, spelling mistakes, abbreviations, implicit objectives, and multiple objectives. It must not invent entities or capabilities.

Examples:
- "Give bangalore travel trios" -> travel information, Bengaluru, knowledge/RAG only.
- "Plan five days in Dubai from Bangalore" -> trip planning, Bengaluru -> Dubai, itinerary; no flights unless requested.
- "Find flights and hotels to Dubai and check weather" -> flights + hotels + weather.
- "Tell me why the Eiffel Tower is important" -> knowledge/RAG only.

## Safety rules

1. No keyword list is used in the production initial intent path.
2. Empty/invalid LLM output becomes GENERAL with no capabilities.
3. Capabilities are explicit in the LLM result; unrelated capabilities remain false.
4. Planner is skipped for knowledge-only requests, preventing destination hallucination from planner slot extraction.
5. Planner is used only when trip slots or specialist execution require it.
6. The planner prompt must never invent a destination not grounded in the request/context.
