# Hong Kong Travel Knowledge

- Country: Country: Hong Kong
- Airport: Airport: Hong Kong International Airport
- IATA: HKG
- ICAO: VHHH
- Knowledge type: destination-city / durable-travel-knowledge
## Stable destination knowledge

Hong Kong is a special administrative region and major Asian city. This document contains durable destination context for RAG;
it is not a source of live operational information.

### Core attractions and areas
- Victoria Peak
- Victoria Harbour
- Tsim Sha Tsui
- Central
- Temple Street

### Culture and heritage
Cantonese heritage, international commerce, dense urban districts and harbour geography shape the city.

### Food and local experiences
- dim sum
- roast meats
- wonton noodles

### Airport grounding
- Country: Country: Hong Kong
- Common airport record in the bundled directory: Airport: Hong Kong International Airport
- IATA: HKG
- ICAO: VHHH
- For authoritative airport/city resolution, the `airport_location` database record remains the application source of truth.
- A destination can have multiple airports; do not infer airport choice solely from this document.

### Retrieval and live-data boundary
Use this document for stable destination recognition, history, culture, major attractions and general planning context.
Do not use it for current flight schedules, fares, hotel availability/prices, weather forecasts, attraction opening hours,
transport schedules, visa/entry rules, closures, strikes or travel advisories. Those require live/authoritative sources
through MCP or other approved live integrations.
