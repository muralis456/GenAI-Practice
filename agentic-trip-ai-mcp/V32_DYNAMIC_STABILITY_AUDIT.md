# V32 Dynamic Stability Audit

## Root cause found in the reported hotel gap
The application **did invoke MCP** for the hotel request. The supplied graph log proves the sequence:
- intent classified `HOTEL_SEARCH` with hotels=true and budget=false
- planner routed to hotel
- MCP catalog was discovered
- `search_hotels` was invoked
- MCP returned success
- HotelAgent reported one structured record

The missing UI result therefore was not caused by a missing MCP invocation. The critical boundary was the result contract between provider → HotelAgent → API assembler → browser renderer. V32 strengthens that boundary and adds assembly diagnostics.

## Changes
1. Semantic intent remains the only capability decision path.
2. Latest modification classification is semantic-first with an independent semantic retry; no lexical classifier is used for live modification routing.
3. `budgetScope` is present in latest-request classification as well as initial classification.
4. Planner JSON contract explicitly includes `hotelBudget`.
5. Planner does not infer a return date from a duration for a non-trip specialist lookup.
6. MCP hotel client logs the raw provider payload at DEBUG and warns when no structured hotel envelope is present.
7. MCP hotel client accepts common provider envelopes (`hotels`, `results`, `data.hotels`, `data.results`) without hard-coding a provider-specific schema.
8. Hotel provider requests explicitly ask for identifiable hotel properties rather than articles/listicles.
9. HotelAgent logs every provider record rejected for invalid entity shape or destination mismatch.
10. TripPlanAssembler logs the hotel count crossing the API boundary. This makes provider-vs-assembler-vs-UI loss immediately observable.
11. Browser hotel validation is aligned with the backend contract and no longer applies a stricter 90-character rule that could hide valid backend records.
12. Added regression coverage proving a verified hotel result survives specialist assembly.
13. Existing weather-only/budget isolation remains covered.

## Dynamic architecture contract
The semantic model decides *what the user wants*. Java owns only:
- schema validation
- safety/contract validation
- graph routing
- provider invocation
- result normalization
- persistence
- presentation mapping

Java must not grow another intent keyword list when a new natural-language phrasing appears.

## Important limitation
The uploaded application is the MCP **client**. The separate MCP server implementation is not present in this ZIP, so server-side search quality cannot be repaired here. V32 therefore treats MCP output as untrusted provider data, validates it, logs its payload/shape, and falls back safely when it is not a real hotel entity.
