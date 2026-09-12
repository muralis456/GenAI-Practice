# Chicago Travel Knowledge

- Country: Country: United States
- Airport: Airport: O'Hare International Airport
- IATA: ORD
- ICAO: KORD
- Knowledge type: destination-city / durable-travel-knowledge
## Stable destination knowledge

Chicago is a major city on Lake Michigan in the United States. This document contains durable destination context for RAG;
it is not a source of live operational information.

### Core attractions and areas
- Millennium Park
- Art Institute of Chicago
- Chicago River architecture cruise
- Navy Pier
- Museum Campus

### Culture and heritage
Known for architecture, blues and jazz history, lakefront public spaces and diverse immigrant communities.

### Food and local experiences
- deep-dish pizza
- Chicago-style hot dog
- Italian beef

### Airport grounding
- Country: Country: United States
- Common airport record in the bundled directory: Airport: O'Hare International Airport
- IATA: ORD
- ICAO: KORD
- For authoritative airport/city resolution, the `airport_location` database record remains the application source of truth.
- A destination can have multiple airports; do not infer airport choice solely from this document.

### Retrieval and live-data boundary
Use this document for stable destination recognition, history, culture, major attractions and general planning context.
Do not use it for current flight schedules, fares, hotel availability/prices, weather forecasts, attraction opening hours,
transport schedules, visa/entry rules, closures, strikes or travel advisories. Those require live/authoritative sources
through MCP or other approved live integrations.
