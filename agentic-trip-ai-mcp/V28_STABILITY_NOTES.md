# V28 Stability / Dynamic Semantic Routing

## Core architecture change

Intent capabilities are CURRENT-TURN state, not cumulative conversation state. Historical trip
results remain available in the checkpoint, but they cannot activate specialists for a new request.

The semantic LLM remains the authority for capability selection. No weather-specific, flight-specific,
hotel-specific or keyword/regex intent branch was added.

## RAG

Knowledge retrieval is also decided semantically. A live capability may be accompanied by knowledge
when the user explicitly asks for practical travel guidance/implications. There is no weather-only RAG
exception.

## Trip modifications

A successful HITL modification resumes the existing trip workflow while RUN_* remains selective, so
only the affected specialists execute and the full dashboard can still be reconstructed from retained
state.

## Turn isolation

Every Intent pass clears transient RAG/tips/validation artifacts before executing the new semantic
decision. This prevents a prior trip's RAG answer, tips, or validation messages from appearing in a
new specialist response.

The specialist UI renders knowledge guidance whenever the semantic RAG capability actually produced it,
without a weather-specific branch.
