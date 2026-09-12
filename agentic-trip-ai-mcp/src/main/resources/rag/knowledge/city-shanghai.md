# Shanghai Travel Knowledge

- Country: Country: China
- Airport: Airport: Shanghai Pudong International Airport
- IATA: PVG
- ICAO: ZSPD
- Knowledge type: destination-city / durable-travel-knowledge
## Stable destination knowledge

Shanghai is a major city on China's east coast. This document contains durable destination context for RAG;
it is not a source of live operational information.

### Core attractions and areas
- The Bund
- Yu Garden
- Shanghai Museum
- French Concession
- Pudong skyline

### Culture and heritage
Historic treaty-port architecture, Chinese commercial heritage and a modern global financial center define Shanghai.

### Food and local experiences
- xiaolongbao
- Shanghainese cuisine
- noodles and local snacks

### Airport grounding
- Country: Country: China
- Common airport record in the bundled directory: Airport: Shanghai Pudong International Airport
- IATA: PVG
- ICAO: ZSPD
- For authoritative airport/city resolution, the `airport_location` database record remains the application source of truth.
- A destination can have multiple airports; do not infer airport choice solely from this document.

### Retrieval and live-data boundary
Use this document for stable destination recognition, history, culture, major attractions and general planning context.
Do not use it for current flight schedules, fares, hotel availability/prices, weather forecasts, attraction opening hours,
transport schedules, visa/entry rules, closures, strikes or travel advisories. Those require live/authoritative sources
through MCP or other approved live integrations.
