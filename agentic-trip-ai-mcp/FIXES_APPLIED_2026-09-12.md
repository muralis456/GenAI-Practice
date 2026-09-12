# Flight Search + Full-Width UI Fixes

## 1. Flight search root cause

Prompt-only requests such as `I need to travel from bangalore to dubai. give me flight details and best hotels for 5 days trip` were classified with both execution capabilities (`needsFlights=true`, `needsHotels=true`) and `needsKnowledge=true` by semantic arbitration.

The graph previously routed any `needsKnowledge=true` request directly to RAG, skipping Planner. Because the DTO destination/departure fields were blank for a prompt-only request, AirportResolverNode later attempted to resolve an empty destination. The logs showed `Airport query is required` and the FlightAgent then returned no usable flight result.

### Fix

Execution capabilities now take precedence over the knowledge flag at the Intent -> Planner boundary. Pure knowledge/research requests still go directly to RAG, while requests that need flights/hotels/weather/budget/itinerary always pass through Planner first.

## 2. Destination recovery

AirportResolverNode and FlightAgentService now recover a destination from the current user request when the structured destination slot is missing. They validate an existing destination against the local airport directory first, so simple input typos such as `dubaig` can fall back to the known `Dubai` hint instead of sending a bad/blank value to MCP.

Blank airport queries are also rejected locally by the MCP airport client/tool path, preventing unnecessary `resolve_airport` calls with an empty argument.

## 3. Full-width result panels

The result grid was changed from two columns to one column. Flights, hotels, weather and budget panels now use the full available result-card width instead of leaving a large empty half of the card, matching the supplied screenshot/request.

## Validation

The source was statically inspected after the changes. Maven tests could not be executed in the available runtime because Maven is not installed (`mvn: command not found`). Run `mvn test` in the normal development environment before deployment.
