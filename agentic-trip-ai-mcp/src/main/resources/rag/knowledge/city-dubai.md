# Dubai Travel Knowledge

- Country: Country: United Arab Emirates
- Airport: Airport: Dubai International Airport
- IATA: DXB
- ICAO: OMDB
- Knowledge type: destination-city / durable-travel-knowledge
## Stable destination knowledge

Dubai is a major city and emirate in the United Arab Emirates. This document contains durable destination context for RAG;
it is not a source of live operational information.

### Core attractions and areas
- Burj Khalifa
- Dubai Creek
- Al Fahidi Historical Neighbourhood
- Museum of the Future
- Jumeirah

### Culture and heritage
A global business and tourism hub combining Gulf heritage districts, contemporary architecture and multicultural communities.

### Food and local experiences
- Emirati dishes
- Arabic grills
- South Asian and international cuisine

### Airport grounding
- Country: Country: United Arab Emirates
- Common airport record in the bundled directory: Airport: Dubai International Airport
- IATA: DXB
- ICAO: OMDB
- For authoritative airport/city resolution, the `airport_location` database record remains the application source of truth.
- A destination can have multiple airports; do not infer airport choice solely from this document.

### Retrieval and live-data boundary
Use this document for stable destination recognition, history, culture, major attractions and general planning context.
Do not use it for current flight schedules, fares, hotel availability/prices, weather forecasts, attraction opening hours,
transport schedules, visa/entry rules, closures, strikes or travel advisories. Those require live/authoritative sources
through MCP or other approved live integrations.
