# Los Angeles Travel Knowledge

- Country: Country: United States
- Airport: Airport: Los Angeles International Airport
- IATA: LAX
- ICAO: KLAX
- Knowledge type: destination-city / durable-travel-knowledge
## Stable destination knowledge

Los Angeles is a major city in Southern California, United States. This document contains durable destination context for RAG;
it is not a source of live operational information.

### Core attractions and areas
- Griffith Observatory
- Hollywood
- Getty Center
- Santa Monica
- Downtown LA

### Culture and heritage
Film and entertainment, multicultural neighborhoods, Pacific beaches and car-oriented urban geography shape Los Angeles.

### Food and local experiences
- Mexican-American cuisine
- Korean food
- tacos and diverse global cuisine

### Airport grounding
- Country: Country: United States
- Common airport record in the bundled directory: Airport: Los Angeles International Airport
- IATA: LAX
- ICAO: KLAX
- For authoritative airport/city resolution, the `airport_location` database record remains the application source of truth.
- A destination can have multiple airports; do not infer airport choice solely from this document.

### Retrieval and live-data boundary
Use this document for stable destination recognition, history, culture, major attractions and general planning context.
Do not use it for current flight schedules, fares, hotel availability/prices, weather forecasts, attraction opening hours,
transport schedules, visa/entry rules, closures, strikes or travel advisories. Those require live/authoritative sources
through MCP or other approved live integrations.
