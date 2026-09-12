# London Travel Knowledge

- Country: Country: United Kingdom
- Airport: Airport: Heathrow Airport
- IATA: LHR
- ICAO: EGLL
- Knowledge type: destination-city / durable-travel-knowledge
## Stable destination knowledge

London is a capital of the United Kingdom. This document contains durable destination context for RAG;
it is not a source of live operational information.

### Core attractions and areas
- Tower of London
- British Museum
- Westminster
- National Gallery
- South Bank

### Culture and heritage
A global city with British royal and parliamentary heritage, world-class museums and diverse communities.

### Food and local experiences
- fish and chips
- Sunday roast
- multicultural food scene

### Airport grounding
- Country: Country: United Kingdom
- Common airport record in the bundled directory: Airport: Heathrow Airport
- IATA: LHR
- ICAO: EGLL
- For authoritative airport/city resolution, the `airport_location` database record remains the application source of truth.
- A destination can have multiple airports; do not infer airport choice solely from this document.

### Retrieval and live-data boundary
Use this document for stable destination recognition, history, culture, major attractions and general planning context.
Do not use it for current flight schedules, fares, hotel availability/prices, weather forecasts, attraction opening hours,
transport schedules, visa/entry rules, closures, strikes or travel advisories. Those require live/authoritative sources
through MCP or other approved live integrations.
