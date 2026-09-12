# RAG + MCP UI Observability

The chat response remains clean, while an expandable **Agent Execution & Sources**
panel exposes:

- RAG retrieval method, query, candidate count, reranked count, iterations and evidence score
- RAG source documents
- MCP tool names used by the execution
- Agent execution timeline
- Existing provenance and execution history

The observability data is assembled from `TravelState` and `AgentStep`; no raw RAG
context or internal retrieval instructions are shown in the normal answer.
