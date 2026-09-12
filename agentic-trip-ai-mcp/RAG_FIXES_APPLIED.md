# RAG Fixes Applied

## 1. User-facing RAG answer generation
- Added `RagAnswerService`.
- Knowledge-only requests now pass grounded context to a final LLM answer-generation step.
- Raw `[Source: ...]` documents are never used as the final user-facing answer.
- Insufficient evidence produces a safe refusal instead of best-effort hallucination.
- Knowledge-only finalization no longer requests HITL approval.

## 2. Retrieval quality
- Reranker scores are now honored.
- Candidates below the minimum relevance threshold are removed instead of being blindly returned.
- This prevents weak city/airport-directory records from being presented as answers to unrelated questions.

## 3. Knowledge corpus
- Expanded every seeded `city-*.md` destination record from airport-only metadata into durable destination knowledge:
  attractions, culture/heritage, food/local experiences, airport grounding and live-data boundaries.
- Added dedicated `destination-paris-eiffel-tower.md` with historical/cultural Eiffel Tower knowledge.
- Added `web-research-provenance.md` with authoritative Paris Tourism and Eiffel Tower sources.
- Updated RAG README with answer-generation and live-data policy.

## 4. Reindex required
After deploying these changes, rebuild the pgvector corpus:

`POST /api/rag/reindex`

This is required because the application intentionally keeps an existing vector index on normal startup.

## Expected tests
- `What airport serves Paris and what is its IATA code?`
  -> natural-language answer such as `Paris is served by Charles de Gaulle Airport (CDG).`
- `What is the history and cultural significance of the Eiffel Tower?`
  -> grounded answer using the new Eiffel Tower document.
- A question with no matching durable evidence
  -> explicit insufficient-information response.
- Knowledge-only query
  -> Intent -> RAG -> Final -> Complete, without Flight/Hotel/Research/Weather/Supervisor/HITL.

## Verification limitation
The source was inspected and modified, but a full Maven build was not run in this environment because the project targets Java 26 while the available JDK is Java 21.
