# Vienna Travel Knowledge

- Country: Country: Austria
- Airport: Airport: Vienna International Airport
- IATA: VIE
- ICAO: LOWW
- Knowledge type: destination-city / durable-travel-knowledge
## Stable destination knowledge

Vienna is a capital of Austria. This document contains durable destination context for RAG;
it is not a source of live operational information.

### Core attractions and areas
- Schönbrunn Palace
- Hofburg
- St Stephen's Cathedral
- Belvedere
- MuseumsQuartier

### Culture and heritage
Habsburg imperial heritage, classical music, coffeehouse culture and Central European architecture define Vienna.

### Food and local experiences
- Viennese coffeehouse pastries
- Wiener schnitzel
- Sachertorte

### Airport grounding
- Country: Country: Austria
- Common airport record in the bundled directory: Airport: Vienna International Airport
- IATA: VIE
- ICAO: LOWW
- For authoritative airport/city resolution, the `airport_location` database record remains the application source of truth.
- A destination can have multiple airports; do not infer airport choice solely from this document.

### Retrieval and live-data boundary
Use this document for stable destination recognition, history, culture, major attractions and general planning context.
Do not use it for current flight schedules, fares, hotel availability/prices, weather forecasts, attraction opening hours,
transport schedules, visa/entry rules, closures, strikes or travel advisories. Those require live/authoritative sources
through MCP or other approved live integrations.
