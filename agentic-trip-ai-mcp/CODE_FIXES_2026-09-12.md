# Code fixes - 2026-09-12

## 1. Full-width UI
- Removed the horizontal space reserved by the desktop sidebar (`display:none`).
- Changed `.content` from `max-width: 860px` to full available width.
- Removed centered/max-width layout constraints that caused the large blank left area.

## 2. New Chat / Send button
- New Chat now clears the prompt, hides any stale loading state, enables the send button, and focuses the textarea.
- The send button remains enabled while the composer is idle. Empty submissions are still rejected by the submit handler.

## 3. Destination extraction
- A deterministic destination found in the current request now takes precedence over noisy LLM extraction.
- Common typo aliases including `dubaig` and `dubia` normalize to `Dubai`.
- This prevents downstream flight/hotel agents from receiving an empty or malformed destination.

## 4. Hotel result safety
- When MCP hotel search is enabled, the hotel agent uses the structured MCP result directly instead of sending the destination through a second LLM tool-call/extraction loop.
- Hotel results are checked for destination relevance before they reach the UI.
- Unrelated results such as Nepal/Italy/Sri Lanka hotels are no longer rendered for a Dubai request; the agent falls back safely when MCP returns no destination-relevant records.

## Expected test
Input:
`I need to travel from bangalore to dubaig. give me flight details and best hotels for 5 days trip`

Expected destination:
`Dubai`

Expected flight route:
`BLR -> DXB` (subject to MCP/AviationStack availability)

Expected hotel behavior:
Only destination-relevant Dubai/UAE hotel records are rendered. If the MCP server returns unrelated records, they are rejected rather than displayed as Dubai hotels.
