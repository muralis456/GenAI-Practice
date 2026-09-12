# Istanbul Travel Knowledge

- Country: Country: Turkey
- Airport: Airport: Istanbul Airport
- IATA: IST
- ICAO: LTFM
- Knowledge type: destination-city / durable-travel-knowledge
## Stable destination knowledge

Istanbul is a major city spanning Europe and Asia in Turkey. This document contains durable destination context for RAG;
it is not a source of live operational information.

### Core attractions and areas
- Hagia Sophia
- Blue Mosque
- Topkapi Palace
- Grand Bazaar
- Bosphorus

### Culture and heritage
Byzantine and Ottoman heritage, Islamic architecture, Bosphorus geography and contemporary Turkish city life are central themes.

### Food and local experiences
- kebabs
- meze
- baklava and Turkish breakfast

### Airport grounding
- Country: Country: Turkey
- Common airport record in the bundled directory: Airport: Istanbul Airport
- IATA: IST
- ICAO: LTFM
- For authoritative airport/city resolution, the `airport_location` database record remains the application source of truth.
- A destination can have multiple airports; do not infer airport choice solely from this document.

### Retrieval and live-data boundary
Use this document for stable destination recognition, history, culture, major attractions and general planning context.
Do not use it for current flight schedules, fares, hotel availability/prices, weather forecasts, attraction opening hours,
transport schedules, visa/entry rules, closures, strikes or travel advisories. Those require live/authoritative sources
through MCP or other approved live integrations.
