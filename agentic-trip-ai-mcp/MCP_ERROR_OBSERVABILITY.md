# MCP Error Observability

MCP failures are logged at three levels:

1. `mcp.client.error phase=tool-catalog` when the MCP catalog cannot be read.
2. `mcp.client.error phase=selection` when tool selection fails.
3. `mcp.client.error phase=invocation` with tool, attempt, retryability, exception class, message and duration.
4. `mcp.client.error phase=server-response` when the MCP tool returns `success=false`, including `errorCode` and `message`.
5. Domain adapters (`McpFlightSearchClient`, `McpHotelSearchClient`, `McpWeatherClient`, `McpResearchClient`, `McpAirportClient`) log the operation and exception before returning a safe fallback.

This makes a failed MCP server distinguishable from a valid empty result.
