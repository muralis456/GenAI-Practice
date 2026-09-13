# AgenticTripAI V26

## UI stabilization
- Fixed the travel workspace DOM nesting bug that placed workspace tabs in the right sidebar and made the dashboard appear broken.
- Tabs now occupy the full travel-main column and remain horizontally scrollable on narrow screens.
- Flight cards are grouped by outbound/return and limited to three visible options per direction.
- Invalid article/listicle hotel names are hidden in the UI as an additional defense for older persisted responses.
- Legacy RAG answers containing plan/budget/live-flight facts are hidden instead of rendered.
- Legacy internal LLM/quota failure strings are never rendered as travel tips.

## Backend safety
- RAG answer validation rejects trip-plan facts even when they appear inside ordinary bullets.
- RAG grounded fallback filters totals, costs, accommodation and date-like trip data.
- Flight normalization rejects explicit route-mismatched records, preventing an outbound record from being relabeled as a return flight.
- Hotel entity validation rejects article/listicle titles and overlong research headings.

No intent-classification keyword/regex changes were introduced.
