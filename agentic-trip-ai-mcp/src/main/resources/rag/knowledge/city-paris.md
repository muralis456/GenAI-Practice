# Paris Travel Knowledge

- Country: Country: France
- Airport: Airport: Charles de Gaulle Airport
- IATA: CDG
- ICAO: LFPG
- Knowledge type: destination-city / durable-travel-knowledge
## Stable destination knowledge

Paris is a capital of France. This document contains durable destination context for RAG;
it is not a source of live operational information.

### Core attractions and areas
- Eiffel Tower
- Louvre Museum
- Notre-Dame
- Arc de Triomphe
- Montmartre
- Seine riverbanks

### Culture and heritage
Paris combines more than two millennia of history with art, architecture, fashion, gastronomy and neighborhood culture. The Eiffel Tower, built for the 1889 Exposition Universelle, became a defining symbol of the city.

### Food and local experiences
- croissants and bread
- French pastries
- regional French cuisine

### Airport grounding
- Country: Country: France
- Common airport record in the bundled directory: Airport: Charles de Gaulle Airport
- IATA: CDG
- ICAO: LFPG
- For authoritative airport/city resolution, the `airport_location` database record remains the application source of truth.
- A destination can have multiple airports; do not infer airport choice solely from this document.

### Retrieval and live-data boundary
Use this document for stable destination recognition, history, culture, major attractions and general planning context.
Do not use it for current flight schedules, fares, hotel availability/prices, weather forecasts, attraction opening hours,
transport schedules, visa/entry rules, closures, strikes or travel advisories. Those require live/authoritative sources
through MCP or other approved live integrations.
