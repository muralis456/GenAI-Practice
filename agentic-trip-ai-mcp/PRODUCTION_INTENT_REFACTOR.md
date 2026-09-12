# Production Intent Planning Refactor

This version adds a deterministic semantic safety layer around the LLM intent
classifier and planner.

## What changed

- `TravelIntentNormalizer` normalizes the LLM capability vector after semantic
  classification.
- Multi-objective requests preserve all clearly requested capabilities.
- A trip does not automatically mean flights, hotels, weather, or budget.
- Explicit destination/origin extraction supports both `from A to B` and
  `to B from A` and takes precedence over LLM slot extraction.
- Natural-language travel objectives map to capabilities:
  - destination/attraction/activity requests -> research
  - culture/history/travel guidance -> RAG knowledge
  - accommodation/areas to stay -> hotels
  - current weather/forecast -> weather
  - explicit cost/budget questions -> budget
  - itinerary/day-by-day/duration-based trip planning -> itinerary
  - flight wording -> flights
- Existing graph behavior then routes knowledge through RAG before specialist
  execution.

## Important design principle

The LLM remains the semantic interpreter. Java owns high-confidence validation
and routing safety. This avoids brittle "keyword-only" routing while preventing
a small local model from turning a phrase such as "best places to visit and
their cultural significance" into a destination or silently dropping requested
capabilities.

## Expected Dubai request

For:

"I want to plan a 5-day trip to Dubai from Bangalore. Give me the best places
to visit, their cultural significance, local travel tips, and recommended areas
to stay. Also check the current weather in Dubai and suggest suitable activities
based on the weather."

the capability vector is expected to be:

- origin: Bengaluru
- destination: Dubai
- knowledge/RAG: true
- research: true
- hotels: true
- weather: true
- itinerary: true
- flights: false
- budget: false

The existing graph should therefore execute Planner -> RAG -> Router -> Hotel /
Research / Weather -> Supervisor -> Itinerary -> Validator -> Final.
