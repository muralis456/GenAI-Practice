# Query Normalization & Entity Resolution

## Design

The application no longer maintains a hard-coded typo/alias dictionary for user wording.
Normalization is an AI capability that runs before graph routing:

User request -> conversation context -> LLM normalization -> entity grounding -> graph/router

The LLM is responsible for:
- spelling correction
- grammar/spacing cleanup when it changes no meaning
- abbreviations
- malformed travel terms
- identifying travel entities
- preserving route, dates, quantities and constraints

The model must not invent facts. Ambiguous words are left unchanged.

## Entity grounding

For city/airport entities, the model's interpretation is grounded against the canonical airport directory in the database. The airport lookup service also retains dynamic similarity matching as a safety net; it contains no one-off typo aliases.

## Conversation context

The normalizer receives the current request plus already hydrated origin, destination and date fields. Therefore a follow-up such as:

`any fligts available from hyderabd for today`

can normalize the current-turn origin to `Hyderabad` while inheriting the destination from conversation memory.

## No hard-coded aliases

Do not add entries such as `hyderabd -> Hyderabad` or `banglore -> Bengaluru` to Java code. If a new typo appears, the LLM handles it without a code change.

## Optional external providers

Google Places API (New) can be added as an entity-grounding provider for place names, addresses and POIs. LanguageTool can be added for dedicated spell/grammar checking. Neither is required for the core capability because the project already has an LLM and a canonical airport directory.
