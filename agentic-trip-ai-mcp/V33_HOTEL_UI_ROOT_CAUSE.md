# V33 — Hotel specialist UI root-cause fix

## Root cause
The backend successfully executed MCP `search_hotels`, rejected three article/listicle records, retained two verified hotel records, and the graph/assembler both reported `stateHotelCount=2` / `includeHotels=true`.

The browser then applied a second, independent lexical hotel-name validator (`isLikelyHotelName`) before rendering. This created a second validation boundary that could reject valid backend hotel entities and incorrectly show the empty state.

## Fix
- HotelAgentService remains the authoritative provider/entity validation boundary.
- The browser now renders every backend hotel record that has a non-blank `name`.
- Removed the UI's stricter identity/content filtering from `buildHotelsSection`.
- Added a defensive response-shape fallback so specialist rendering can use `data.hotels` if a response ever arrives without the nested `plan.hotels` envelope.
- Weather and flights get the same harmless top-level fallback for specialist responses.

## Design principle
Do not duplicate semantic/entity validation in the browser. Backend validates provider data; API DTO defines the contract; UI renders the contract.
