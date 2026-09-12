# Seoul Travel Knowledge

- Country: Country: South Korea
- Airport: Airport: Incheon International Airport
- IATA: ICN
- ICAO: RKSI
- Knowledge type: destination-city / durable-travel-knowledge
## Stable destination knowledge

Seoul is a capital of South Korea. This document contains durable destination context for RAG;
it is not a source of live operational information.

### Core attractions and areas
- Gyeongbokgung Palace
- Bukchon Hanok Village
- Myeongdong
- N Seoul Tower
- Insadong

### Culture and heritage
Joseon royal heritage, Korean traditional neighborhoods and highly modern technology and popular culture coexist.

### Food and local experiences
- Korean barbecue
- bibimbap
- street food

### Airport grounding
- Country: Country: South Korea
- Common airport record in the bundled directory: Airport: Incheon International Airport
- IATA: ICN
- ICAO: RKSI
- For authoritative airport/city resolution, the `airport_location` database record remains the application source of truth.
- A destination can have multiple airports; do not infer airport choice solely from this document.

### Retrieval and live-data boundary
Use this document for stable destination recognition, history, culture, major attractions and general planning context.
Do not use it for current flight schedules, fares, hotel availability/prices, weather forecasts, attraction opening hours,
transport schedules, visa/entry rules, closures, strikes or travel advisories. Those require live/authoritative sources
through MCP or other approved live integrations.
