# RAG knowledge-only and MCP avoidance fix

## Problems addressed

1. Small local LLMs can classify a durable knowledge request as both `knowledge=true` and `research=true`.
2. LangGraph node updates are applied after `NodeAction.apply()` returns. Calling `RagAnswerService.answer(state)` from `RagNode` therefore read the pre-RAG state and incorrectly returned the "insufficient evidence" message.
3. Initial `TravelState` contains UI-safe defaults (dates, traveler count, budget label and travel style). Sending those defaults to the RAG decision prompt polluted retrieval.
4. Informational RAG responses should not stop at HITL approval.
5. The UI did not expose the actual RAG answer/result separately.

## Behavior

For a durable knowledge request such as "Bangalore trip precautions":

`INTENT -> RAG -> VALIDATOR -> FINAL -> COMPLETE`

No research MCP call is scheduled when the independent semantic signal determines that knowledge materially dominates live/open-web research.

RAG answer generation consumes the exact `RagResult` produced by the RAG node, so the generated answer is available in state and the UI.

RAG observability is exposed through `AgentExecutionDetails`.

Trip defaults are not sent to the RAG router unless they were explicitly present as known state.

## Semantic, not keyword, routing

The knowledge-vs-research conflict is resolved from embedding similarity. No literal phrase such as "precautions" or "trios" is required in the routing logic.

A meaningful semantic margin is used so genuine multi-intent requests remain multi-intent.
