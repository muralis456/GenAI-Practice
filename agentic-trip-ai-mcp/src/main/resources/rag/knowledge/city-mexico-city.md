# Mexico City Travel Knowledge

- Country: Country: Mexico
- Airport: Airport: Mexico City International Airport
- IATA: MEX
- ICAO: MMMX
- Knowledge type: destination-city / durable-travel-knowledge
## Stable destination knowledge

Mexico City is a capital of Mexico. This document contains durable destination context for RAG;
it is not a source of live operational information.

### Core attractions and areas
- Zócalo
- National Museum of Anthropology
- Chapultepec
- Frida Kahlo Museum
- Palacio de Bellas Artes

### Culture and heritage
Aztec/Mexica heritage, Spanish colonial history, Mexican art and a large contemporary cultural scene define the capital.

### Food and local experiences
- tacos
- mole
- tamales and Mexican regional cuisine

### Airport grounding
- Country: Country: Mexico
- Common airport record in the bundled directory: Airport: Mexico City International Airport
- IATA: MEX
- ICAO: MMMX
- For authoritative airport/city resolution, the `airport_location` database record remains the application source of truth.
- A destination can have multiple airports; do not infer airport choice solely from this document.

### Retrieval and live-data boundary
Use this document for stable destination recognition, history, culture, major attractions and general planning context.
Do not use it for current flight schedules, fares, hotel availability/prices, weather forecasts, attraction opening hours,
transport schedules, visa/entry rules, closures, strikes or travel advisories. Those require live/authoritative sources
through MCP or other approved live integrations.
