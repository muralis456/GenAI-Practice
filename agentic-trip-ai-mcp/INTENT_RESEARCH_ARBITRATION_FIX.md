# Intent research arbitration fix

The embedding-based SemanticIntentArbiter is a recovery signal only. It must not veto a capability explicitly selected by the semantic LLM.

For nuanced requests such as `Bangalore travel precautions`, embeddings may score durable knowledge higher than live research even when the LLM determines that current research is useful. The final merge therefore preserves explicit LLM research=true and only uses the embedding signal to recover missing capabilities when appropriate.

Expected behavior:
- `Bangalore travel precautions` may execute RAG + research when the semantic LLM requests both.
- `Bangalore travel tips` may execute RAG only when the semantic LLM determines the request is durable knowledge.
- `current travel advisories for Bangalore` should execute research/live tools.
- No keyword rule is used to make the decision.
