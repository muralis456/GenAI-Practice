# San Francisco Travel Knowledge

- Country: Country: United States
- Airport: Airport: San Francisco International Airport
- IATA: SFO
- ICAO: KSFO
- Knowledge type: destination-city / durable-travel-knowledge
## Stable destination knowledge

San Francisco is a coastal city in California, United States. This document contains durable destination context for RAG;
it is not a source of live operational information.

### Core attractions and areas
- Golden Gate Bridge
- Alcatraz
- Fisherman's Wharf
- Chinatown
- Golden Gate Park

### Culture and heritage
Pacific geography, immigrant neighborhoods, technology history and distinctive Victorian architecture shape San Francisco.

### Food and local experiences
- sourdough
- seafood
- Mission-style burritos

### Airport grounding
- Country: Country: United States
- Common airport record in the bundled directory: Airport: San Francisco International Airport
- IATA: SFO
- ICAO: KSFO
- For authoritative airport/city resolution, the `airport_location` database record remains the application source of truth.
- A destination can have multiple airports; do not infer airport choice solely from this document.

### Retrieval and live-data boundary
Use this document for stable destination recognition, history, culture, major attractions and general planning context.
Do not use it for current flight schedules, fares, hotel availability/prices, weather forecasts, attraction opening hours,
transport schedules, visa/entry rules, closures, strikes or travel advisories. Those require live/authoritative sources
through MCP or other approved live integrations.
