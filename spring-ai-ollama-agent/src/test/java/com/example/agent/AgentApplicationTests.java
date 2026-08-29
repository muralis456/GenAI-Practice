package com.example.agent;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class AgentApplicationTests {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void chatClientBeanExists() {
        ChatClient chatClient = applicationContext.getBeanProvider(ChatClient.class).getIfAvailable();
        assertThat(chatClient).isNotNull();
    }
}
