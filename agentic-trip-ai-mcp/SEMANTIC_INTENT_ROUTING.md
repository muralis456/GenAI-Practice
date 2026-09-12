# Semantic Intent Routing

The initial intent path is semantic-first and does not use keyword matching.

1. `IntentAgentService` asks the LLM for a structured capability plan.
2. If the LLM returns no capabilities, `SemanticIntentArbiter` uses the existing
   Ollama embedding model to compare the request meaning against capability
   descriptions.
3. If both signals fail, a second LLM adjudication pass is attempted.
4. A request with only `knowledge=true` routes directly to RAG.
5. A request with no capabilities routes directly to FINAL rather than Planner.
   This prevents the planner from inventing destinations or trip slots.
6. The Planner is therefore never a fallback for an unclassified request.

This specifically prevents a short knowledge request from becoming an arbitrary
trip and invoking airport/flight tools.
