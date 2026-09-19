# AgenticRagService compile fix

Fixed the Java compile error:
`Local variable topics is required to be final or effectively final based on its usage`

Changes in `src/main/java/com/example/travel/rag/AgenticRagService.java`:
- Renamed the explicit-knowledge local variable to `inferredTopics` and declared it `final`.
- Renamed the LLM-router topic variable to `extractedTopics` and declared it `final`.
- Updated the `Decision` constructor calls to use those final variables.
- Kept the dynamic Agentic RAG behavior unchanged.

A full Maven build could not be executed in this environment because this project declares Java 26, while the available runtime is Java 21 and Maven is not installed.
