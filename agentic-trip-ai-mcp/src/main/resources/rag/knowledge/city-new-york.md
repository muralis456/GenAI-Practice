# New York Travel Knowledge

- Country: Country: United States
- Airport: Airport: John F. Kennedy International Airport
- IATA: JFK
- ICAO: KJFK
- Knowledge type: destination-city / durable-travel-knowledge
## Stable destination knowledge

New York is a major city in the United States. This document contains durable destination context for RAG;
it is not a source of live operational information.

### Core attractions and areas
- Central Park
- Statue of Liberty
- Metropolitan Museum of Art
- Times Square
- Brooklyn Bridge

### Culture and heritage
Immigration, finance, arts, architecture and neighborhood diversity are central to New York's identity.

### Food and local experiences
- New York pizza
- bagels
- deli and global cuisine

### Airport grounding
- Country: Country: United States
- Common airport record in the bundled directory: Airport: John F. Kennedy International Airport
- IATA: JFK
- ICAO: KJFK
- For authoritative airport/city resolution, the `airport_location` database record remains the application source of truth.
- A destination can have multiple airports; do not infer airport choice solely from this document.

### Retrieval and live-data boundary
Use this document for stable destination recognition, history, culture, major attractions and general planning context.
Do not use it for current flight schedules, fares, hotel availability/prices, weather forecasts, attraction opening hours,
transport schedules, visa/entry rules, closures, strikes or travel advisories. Those require live/authoritative sources
through MCP or other approved live integrations.
