package com.example.travel.service;

import com.example.travel.entity.ConversationMemory;
import com.example.travel.repository.ConversationMemoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ConversationMemoryService {

    private static final Logger log = LoggerFactory.getLogger(ConversationMemoryService.class);
    private static final int MAX_HISTORY = 10;

    private final ConversationMemoryRepository repository;

    public ConversationMemoryService(ConversationMemoryRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void saveMessage(String userId, String sessionId, String role, String content) {
        ConversationMemory memory = new ConversationMemory();
        memory.setUserId(userId);
        memory.setSessionId(sessionId);
        memory.setRole(role);
        memory.setContent(content);
        memory.setCreatedAt(Instant.now());
        repository.save(memory);
        log.debug("Saved conversation memory for userId={}, sessionId={}, role={}", userId, sessionId, role);
    }

    /**
     * Loads recent turns and returns a detached string so the JDBC connection is
     * released before long-running LLM/orchestration work.
     */
    @Transactional(readOnly = true)
    public String buildHistoryContext(String userId, String sessionId) {
        List<ConversationMemory> history = loadRecentHistory(userId, sessionId);
        if (history.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("Previous conversation context:\n");
        for (ConversationMemory memory : history) {
            sb.append(memory.getRole()).append(": ").append(memory.getContent()).append("\n");
        }
        return sb.toString();
    }

    @Transactional(readOnly = true)
    public List<ConversationMemory> getRecentHistory(String userId, String sessionId) {
        return loadRecentHistory(userId, sessionId);
    }

    @Transactional(readOnly = true)
    public List<ConversationMemory> getRecentHistory(String userId) {
        PageRequest pageable = PageRequest.of(0, MAX_HISTORY);
        List<ConversationMemory> memories = repository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        return List.copyOf(memories.stream()
                .sorted((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()))
                .collect(Collectors.toList()));
    }

    private List<ConversationMemory> loadRecentHistory(String userId, String sessionId) {
        PageRequest pageable = PageRequest.of(0, MAX_HISTORY);
        List<ConversationMemory> memories = repository.findByUserIdAndSessionIdOrderByCreatedAtDesc(userId, sessionId, pageable);
        return memories.stream()
                .sorted((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()))
                .collect(Collectors.toList());
    }
}
