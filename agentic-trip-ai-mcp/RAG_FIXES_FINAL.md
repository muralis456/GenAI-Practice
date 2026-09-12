# RAG Final Fixes

## Changes

### 1. Knowledge-only query normalization
For `KNOWLEDGE_QUERY` requests, the retrieval query now uses the user's actual question rather than trip-planning state. Origin, dates, budget, traveler count and travel style are not appended to durable knowledge queries.

Example:
- User: `What is the history and cultural significance of the Eiffel Tower?`
- Retrieval query: `What is the history and cultural significance of the Eiffel Tower?`

Multi-query expansion for knowledge-only requests now stays subject-focused and can add a destination-qualified variant without adding `destination travel` or other planning noise.

### 2. Final-answer metadata protection
The final RAG answer prompt now explicitly excludes:
- source filenames
- RAG/retrieval mechanics
- document metadata
- `RAG usage` sections
- `Live-data boundary` sections
- `Authoritative web references`
- internal tool/indexing instructions

A defense-in-depth sanitizer also removes those sections before the final LLM call.

### 3. Context compression protection
The grounded-context compressor is instructed to retain user-relevant facts only and discard document-control/instructional sections.

### 4. Verified Eiffel Tower knowledge
The Eiffel Tower knowledge document is included and contains durable history/cultural facts grounded in the official Eiffel Tower references stored in the document.

## Expected result
The question:

`What is the history and cultural significance of the Eiffel Tower?`

should produce a direct natural-language answer without exposing RAG metadata or live-data instructions.
