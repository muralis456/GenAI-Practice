# Madrid Travel Knowledge

- Country: Country: Spain
- Airport: Airport: Adolfo Suarez Madrid-Barajas Airport
- IATA: MAD
- ICAO: LEMD
- Knowledge type: destination-city / durable-travel-knowledge
## Stable destination knowledge

Madrid is a capital of Spain. This document contains durable destination context for RAG;
it is not a source of live operational information.

### Core attractions and areas
- Prado Museum
- Royal Palace
- Retiro Park
- Plaza Mayor
- Gran Vía

### Culture and heritage
Spanish royal heritage, art museums, public plazas and late-evening urban culture define Madrid.

### Food and local experiences
- tapas
- cocido madrileño
- bocadillo de calamares

### Airport grounding
- Country: Country: Spain
- Common airport record in the bundled directory: Airport: Adolfo Suarez Madrid-Barajas Airport
- IATA: MAD
- ICAO: LEMD
- For authoritative airport/city resolution, the `airport_location` database record remains the application source of truth.
- A destination can have multiple airports; do not infer airport choice solely from this document.

### Retrieval and live-data boundary
Use this document for stable destination recognition, history, culture, major attractions and general planning context.
Do not use it for current flight schedules, fares, hotel availability/prices, weather forecasts, attraction opening hours,
transport schedules, visa/entry rules, closures, strikes or travel advisories. Those require live/authoritative sources
through MCP or other approved live integrations.
