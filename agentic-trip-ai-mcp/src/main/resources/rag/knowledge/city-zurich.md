# Zurich Travel Knowledge

- Country: Country: Switzerland
- Airport: Airport: Zurich Airport
- IATA: ZRH
- ICAO: LSZH
- Knowledge type: destination-city / durable-travel-knowledge
## Stable destination knowledge

Zurich is a largest city in Switzerland. This document contains durable destination context for RAG;
it is not a source of live operational information.

### Core attractions and areas
- Lake Zurich
- Old Town
- Grossmünster
- Kunsthaus Zürich
- Bahnhofstrasse

### Culture and heritage
Swiss financial and cultural life meets a historic old town, lake setting and easy access to Alpine landscapes.

### Food and local experiences
- Swiss cheese dishes
- rösti
- Swiss chocolate

### Airport grounding
- Country: Country: Switzerland
- Common airport record in the bundled directory: Airport: Zurich Airport
- IATA: ZRH
- ICAO: LSZH
- For authoritative airport/city resolution, the `airport_location` database record remains the application source of truth.
- A destination can have multiple airports; do not infer airport choice solely from this document.

### Retrieval and live-data boundary
Use this document for stable destination recognition, history, culture, major attractions and general planning context.
Do not use it for current flight schedules, fares, hotel availability/prices, weather forecasts, attraction opening hours,
transport schedules, visa/entry rules, closures, strikes or travel advisories. Those require live/authoritative sources
through MCP or other approved live integrations.
