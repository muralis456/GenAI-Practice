# V23 — Dynamic Semantic Intent Architecture

## Goal
User requests must be interpreted by meaning, not by Java keyword/regex trigger lists.

## Decision pipeline
1. Primary semantic LLM classifies the complete latest request into the fixed capability contract.
2. If the result is confident and contains capabilities, it is authoritative.
3. If the result is empty or low-confidence, a second independent semantic LLM pass adjudicates the request.
4. Java validates the JSON contract and derives routing metadata from the returned boolean capability vector only.
5. Embedding similarity is not allowed to veto or overwrite a valid LLM capability plan.
6. If both semantic passes are genuinely uncertain, return GENERAL safely rather than guessing.

## What Java does NOT do
- No keyword/substring capability detection.
- No regex-based trip intent detection.
- No special-case phrase list for budgets, trips, weather, hotels, flights, etc.
- No capability inference from the presence of a destination, route, duration, or currency string.

## What remains deterministic
Deterministic code is limited to execution safety, schema validation, state management, graph routing, and deriving a request type from the already-classified capability vector. Slot extraction/normalization is separate from intent classification.

## Stability principle
Future user phrasings should normally be handled by the semantic model without modifying Java intent code. A code change is warranted only when the capability contract, safety policy, model, or graph architecture changes—not because a user used a new sentence.
