# Hotel provider fix — 2026-09-13

## Problem
The MCP hotel capability was returning article/listicle titles as structured hotel `name` values, for example:
- Best Tokyo Mid-Range and Business Hotels 2026
- Best Value Hotels in Tokyo 2026
- The 10 Best Hotels for a Weekend Getaway...

The application correctly rejected those values, but then had weak recovery because the fallback relied on a single broad web query.

## Fix
1. `McpHotelSearchClient` now prefers the exact `search_hotels` MCP capability when it is exposed, avoiding an unnecessary LLM tool-selection step for a hotel-domain request. If that exact capability is unavailable, dynamic selection remains the fallback.
2. Hotel fallback research now uses three property-oriented searches and deduplicates results before extraction.
3. Obvious article/listicle/search-heading titles are filtered before the extraction LLM sees them.
4. The extraction contract remains strict: only real, identifiable hotel properties may become `HotelOption` records; article titles are never promoted to hotel entities.
5. Existing destination relevance validation remains in place.

## Expected log behavior
It is still valid to see a warning for rejected provider records. The important change is that the agent should then recover from property-oriented research when valid hotel search results are available, instead of treating the rejected listicle titles as hotels.

Note: the separate MCP server implementation is not included in this application ZIP. If the server itself is incapable of returning property records, server-side changes are still required; this client now has a stronger and safer recovery path.
