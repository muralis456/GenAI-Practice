# Tokyo Travel Knowledge

- Country: Country: Japan
- Airport: Airport: Narita International Airport
- IATA: NRT
- ICAO: RJAA
- Knowledge type: destination-city / durable-travel-knowledge
## Stable destination knowledge

Tokyo is a capital of Japan. This document contains durable destination context for RAG;
it is not a source of live operational information.

### Core attractions and areas
- Tokyo Skytree
- Meiji Shrine
- Asakusa
- Shibuya
- Tokyo National Museum

### Culture and heritage
Traditional Japanese heritage and highly modern urban districts coexist across a very large metropolitan area.

### Food and local experiences
- sushi
- ramen
- tempura and regional Japanese food

### Airport grounding
- Country: Country: Japan
- Common airport record in the bundled directory: Airport: Narita International Airport
- IATA: NRT
- ICAO: RJAA
- For authoritative airport/city resolution, the `airport_location` database record remains the application source of truth.
- A destination can have multiple airports; do not infer airport choice solely from this document.

### Retrieval and live-data boundary
Use this document for stable destination recognition, history, culture, major attractions and general planning context.
Do not use it for current flight schedules, fares, hotel availability/prices, weather forecasts, attraction opening hours,
transport schedules, visa/entry rules, closures, strikes or travel advisories. Those require live/authoritative sources
through MCP or other approved live integrations.
