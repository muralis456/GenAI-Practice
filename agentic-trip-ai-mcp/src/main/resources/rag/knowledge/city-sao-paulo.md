# Sao Paulo Travel Knowledge

- Country: Country: Brazil
- Airport: Airport: Sao Paulo-Guarulhos International Airport
- IATA: GRU
- ICAO: SBGR
- Knowledge type: destination-city / durable-travel-knowledge
## Stable destination knowledge

Sao Paulo is a largest city in Brazil. This document contains durable destination context for RAG;
it is not a source of live operational information.

### Core attractions and areas
- Paulista Avenue
- Ibirapuera Park
- MASP
- Liberdade
- Municipal Market

### Culture and heritage
A highly diverse metropolis shaped by Brazilian, Japanese, Italian, Arab and other immigrant communities.

### Food and local experiences
- Brazilian barbecue
- pastel
- Japanese-Brazilian cuisine

### Airport grounding
- Country: Country: Brazil
- Common airport record in the bundled directory: Airport: Sao Paulo-Guarulhos International Airport
- IATA: GRU
- ICAO: SBGR
- For authoritative airport/city resolution, the `airport_location` database record remains the application source of truth.
- A destination can have multiple airports; do not infer airport choice solely from this document.

### Retrieval and live-data boundary
Use this document for stable destination recognition, history, culture, major attractions and general planning context.
Do not use it for current flight schedules, fares, hotel availability/prices, weather forecasts, attraction opening hours,
transport schedules, visa/entry rules, closures, strikes or travel advisories. Those require live/authoritative sources
through MCP or other approved live integrations.
