# Melbourne Travel Knowledge

- Country: Country: Australia
- Airport: Airport: Melbourne Airport
- IATA: MEL
- ICAO: YMML
- Knowledge type: destination-city / durable-travel-knowledge
## Stable destination knowledge

Melbourne is a major city in Victoria, Australia. This document contains durable destination context for RAG;
it is not a source of live operational information.

### Core attractions and areas
- Federation Square
- National Gallery of Victoria
- laneways
- Queen Victoria Market
- Great Ocean Road gateway

### Culture and heritage
Arts, sport, coffee culture, multicultural neighborhoods and Victorian architecture are major themes.

### Food and local experiences
- coffee
- Australian brunch
- multicultural cuisine

### Airport grounding
- Country: Country: Australia
- Common airport record in the bundled directory: Airport: Melbourne Airport
- IATA: MEL
- ICAO: YMML
- For authoritative airport/city resolution, the `airport_location` database record remains the application source of truth.
- A destination can have multiple airports; do not infer airport choice solely from this document.

### Retrieval and live-data boundary
Use this document for stable destination recognition, history, culture, major attractions and general planning context.
Do not use it for current flight schedules, fares, hotel availability/prices, weather forecasts, attraction opening hours,
transport schedules, visa/entry rules, closures, strikes or travel advisories. Those require live/authoritative sources
through MCP or other approved live integrations.
