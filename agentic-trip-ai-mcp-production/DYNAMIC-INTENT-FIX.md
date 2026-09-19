# Dynamic intent fix

This version fixes an important orchestration leak: a semantic capability flag could promote a specialist/advice request into unrelated live capabilities.

## Rules

- `TRIP_PLANNING` is the only intent that expands into the complete trip contract.
- `ITINERARY` remains itinerary-only.
- Generic travel wording does not imply weather.
- Precautions/safety/packing/practical travel advice remain knowledge unless live weather is explicitly requested.
- Knowledge is not automatically research.
- Research is retained when the user asks for attractions, activities, recommendations, places to visit, etc.
- The safety guard only removes clearly over-inferred capabilities; it never invents new capabilities.
- Planner-side task filtering continues to enforce the canonical capability contract.

Example:

`give me when if want to travel bangalore what precautions i need to take care`

becomes:

`knowledge -> answer`

not:

`weather + knowledge + itinerary`.
