# AgenticTripAI fixes – 2026-09-12 V2

## Route / airport / hotel fix

The previous version could still reach FlightAgent/HotelAgent with an empty destination because the API request commonly provides only `prompt`/`preferences`, while DTO `destination` is blank.

Changes:
- `TravelState.fromRequest()` now seeds origin/destination from the current prompt before graph execution.
- `TripSlotHeuristics` now uses route-aware extraction for `from X to Y` requests.
- Destination extraction uses the route destination, so `from Bangalore to dubaig` resolves to `Dubai` rather than incorrectly selecting the first place mentioned.
- `dubaig`/`dubia` normalize to `Dubai`.
- `AirportResolverNode` never invokes the airport resolver with a blank destination.
- `AirportResolverNode` persists recovered destination into graph state for downstream specialists.
- `HotelAgentService` has a second deterministic destination recovery guard.
- Hotel search refuses to invent unrelated results when destination is missing.

## UI layout fix

The requested desktop layout is the original two-pane UI:
- Sidebar remains visible at 300px.
- Main conversation occupies all remaining horizontal space.
- Removed the 920px centered content restriction that caused the large left gap.
- Assistant/live result cards use the available conversation width.
- Mobile breakpoint still switches to the existing stacked layout.
- Existing New Chat composer behavior remains: idle send button is enabled and empty submissions are ignored.
