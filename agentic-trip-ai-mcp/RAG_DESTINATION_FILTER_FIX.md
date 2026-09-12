# RAG destination-filter fix

## Root cause

The LLM returned a display destination such as `Paris, France`, while the seeded vector metadata uses the canonical city key `paris`.

The old normalization produced only `paris-france`, so the vector/FTS filters excluded the Eiffel Tower document even though it existed in `vector_store`.

## Fix

- Destination normalization now adds comma-separated components (`paris`, `france`) in addition to the display key (`paris-france`).
- Vector retrieval retries without a destination filter if a valid filtered search returns zero documents.
- PostgreSQL FTS retrieval does the same fallback.
- When no destination is known, retrieval no longer filters to `destinationKey=global`; it searches the complete durable knowledge base and lets RRF/reranking determine relevance.
- Added `destination-paris-eiffel-tower.md` with durable historical/cultural knowledge grounded in official Eiffel Tower sources.

## Expected log

For `What is the history and cultural significance of the Eiffel Tower?`, expected destination keys include:

`[paris-france, paris, france]`

and retrieval should include:

`destination-paris-eiffel-tower.md`
