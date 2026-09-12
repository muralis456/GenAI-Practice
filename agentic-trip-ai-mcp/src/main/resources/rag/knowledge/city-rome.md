# Rome Travel Knowledge

- Country: Country: Italy
- Airport: Airport: Leonardo da Vinci International Airport
- IATA: FCO
- ICAO: LIRF
- Knowledge type: destination-city / durable-travel-knowledge
## Stable destination knowledge

Rome is a capital of Italy. This document contains durable destination context for RAG;
it is not a source of live operational information.

### Core attractions and areas
- Colosseum
- Roman Forum
- Pantheon
- Trevi Fountain
- Vatican City

### Culture and heritage
Ancient Roman, Renaissance, Baroque and Catholic heritage make Rome one of Europe's most historically layered cities.

### Food and local experiences
- pasta
- pizza romana
- gelato

### Airport grounding
- Country: Country: Italy
- Common airport record in the bundled directory: Airport: Leonardo da Vinci International Airport
- IATA: FCO
- ICAO: LIRF
- For authoritative airport/city resolution, the `airport_location` database record remains the application source of truth.
- A destination can have multiple airports; do not infer airport choice solely from this document.

### Retrieval and live-data boundary
Use this document for stable destination recognition, history, culture, major attractions and general planning context.
Do not use it for current flight schedules, fares, hotel availability/prices, weather forecasts, attraction opening hours,
transport schedules, visa/entry rules, closures, strikes or travel advisories. Those require live/authoritative sources
through MCP or other approved live integrations.
