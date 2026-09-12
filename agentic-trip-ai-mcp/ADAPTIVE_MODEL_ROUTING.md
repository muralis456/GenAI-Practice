# Adaptive Model Routing

The graph now derives execution complexity from the semantic capability plan rather than matching user words.

- SIMPLE: 0-1 requested capabilities -> `travel.models.fast` fallback to role model
- NORMAL: 2-3 capabilities -> role-specific model, then balanced, then fast
- COMPLEX: 4+ capabilities -> `travel.models.reasoning` fallback to role model
- Explicit FAST/REASONING/concrete model selected by the user still overrides adaptive routing.

Configure stronger local Ollama models through environment variables:

- `TRAVEL_MODEL_FAST`
- `TRAVEL_MODEL_BALANCED`
- `TRAVEL_MODEL_REASONING`
- `TRAVEL_MODEL_PLANNER`
- `TRAVEL_MODEL_FINAL`
- `TRAVEL_MODEL_ITINERARY`

The runtime log now includes `complexity=SIMPLE|NORMAL|COMPLEX`, making model-selection decisions auditable per graph node.
