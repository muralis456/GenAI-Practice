# V27 Stability Fixes

## Specialist response isolation

- Specialist responses render through the specialist dashboard whenever `tripPlanning=false`.
- Weather, flight, hotel, budget and research requests no longer render the full trip dashboard.
- `TripPlanAssembler` uses `RUN_*` for specialist responses and cumulative `NEEDS_*` only for actual trip-planning workflows.
- This prevents previous checkpoint capabilities/results from leaking into a later specialist response.

## Weather + knowledge

- Weather-only requests receive a capability-level RAG enrichment pass for durable destination guidance.
- No request-word/regex intent classification was added.
- Live weather remains the primary answer; RAG guidance is shown separately.

## Safe defaults

- `TravelState.requestType` and schema default to `GENERAL`, preventing an unclassified request from becoming a trip plan.
- `IntentPlan` defaults to `GENERAL`; `fullTrip()` now explicitly initializes the complete trip capability vector.

## Regression intent

Example:

`what weather in bangalore for 2 days travel`

Expected graph:

`Intent -> RAG -> Router -> Weather -> Supervisor -> Validator -> Final -> Complete`

Expected UI:

- Weather result only
- Travel Tips & Guidance/RAG card separately
- No Trip Summary
- No Budget card
- No Flights/Hotels/Itinerary cards
- No approval controls
