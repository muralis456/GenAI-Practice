package com.example.travel;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
    "app.browser.enabled=false",
    "spring.ai.mcp.client.enabled=false",
    "travel.mcp.client.enabled=false",
    "spring.ai.vectorstore.pgvector.initialize-schema=false",
    "travel.rag.enabled=false",
    "travel.rag.seed-on-empty=false"
})
class AgenticTripAiApplicationTests {

    @Test
    void contextLoads() {
    }
}
