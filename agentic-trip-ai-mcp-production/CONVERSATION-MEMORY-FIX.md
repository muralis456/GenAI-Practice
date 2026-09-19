# Conversation Memory / Follow-up Fix

## What changed

The travel agent now has a backend-owned conversation boundary that is separate from the LangGraph execution `threadId`.

- `conversationId` was added to `TravelRequest`.
- `ConversationMemory` now stores `conversationId` in addition to `sessionId`/graph `threadId`.
- Every follow-up graph run can use a new `threadId` while retaining the same `conversationId`.
- User and assistant turns from streaming execution are persisted under the stable conversation.
- Backend history is loaded by `conversationId`, not by the latest graph thread.
- Structured assistant responses are included in bounded agent history so destination, weather, RAG answers, hotels, flights, etc. can be remembered.
- Missing request slots are hydrated from the latest structured assistant response before graph execution. This makes follow-ups work for API clients as well as the browser.
- Added `/api/chat/conversation/{conversationId}` for restoring a complete conversation.
- Conversation deletion now supports `conversationId`.
- Frontend sends a stable conversation id and restores server conversations by conversation id.

## Example

Turn 1:
`I want to travel to Bengaluru. What precautions should I take?`

Turn 2:
`What is the weather currently?`

The second request can omit the destination. The backend loads the previous conversation, hydrates `destination=Bengaluru`, and passes the prior conversation context into the new graph execution.

## Compatibility

Existing `sessionId`/thread-based APIs remain available. Existing rows without `conversationId` remain valid because the new database column is nullable and Spring/JPA `ddl-auto: update` can add it.
