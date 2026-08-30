package com.example.travel.service;

import com.example.travel.entity.ConversationMemory;
import com.example.travel.repository.ConversationMemoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ConversationMemoryService {

    private static final Logger log = LoggerFactory.getLogger(ConversationMemoryService.class);
    private static final int MAX_HISTORY = 6;
    private static final int MAX_CONTENT = 400;
    private static final int UI_HISTORY_LIMIT = 50;
    private static final int UI_CONTENT_MAX = 12000;

    private final ConversationMemoryRepository repository;

    public ConversationMemoryService(ConversationMemoryRepository repository) {
        this.repository = repository;
    }

    /**
     * Commits immediately (REQUIRES_NEW) so a search is stored even if the long
     * travel graph later fails or the HTTP client times out.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveMessage(String userId, String sessionId, String role, String content) {
        ConversationMemory memory = new ConversationMemory();
        memory.setUserId(userId);
        memory.setSessionId(sessionId);
        memory.setRole(role);
        memory.setContent(truncate(content, MAX_CONTENT));
        memory.setCreatedAt(Instant.now());
        ConversationMemory saved = repository.saveAndFlush(memory);
        log.info("Saved conversation memory id={} userId={} role={} chars={}",
                saved.getId(), userId, role, memory.getContent().length());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveUiMessage(String userId, String sessionId, String role, String content) {
        ConversationMemory memory = new ConversationMemory();
        memory.setUserId(userId);
        memory.setSessionId(sessionId);
        memory.setRole(role);
        memory.setContent(truncate(content, UI_CONTENT_MAX));
        memory.setCreatedAt(Instant.now());
        ConversationMemory saved = repository.saveAndFlush(memory);
        log.info("Saved UI conversation memory id={} userId={} role={} chars={}",
                saved.getId(), userId, role, memory.getContent().length());
    }

    @Transactional(readOnly = true)
    public long countForUser(String userId) {
        return repository.countByUserId(userId);
    }

    @Transactional(readOnly = true)
    public List<HistoryItem> getHistoryForUi(String userId, int limit) {
        int size = Math.min(Math.max(limit, 1), UI_HISTORY_LIMIT);
        PageRequest pageable = PageRequest.of(0, size);
        List<ConversationMemory> memories = repository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        log.info("Loaded {} history row(s) for userId={}", memories.size(), userId);
        return memories.stream()
                .sorted((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()))
                .map(m -> new HistoryItem(
                        m.getId(),
                        m.getRole(),
                        m.getContent(),
                        m.getCreatedAt() == null ? null : m.getCreatedAt().toString()))
                .collect(Collectors.toList());
    }

    public record HistoryItem(Long id, String role, String content, String createdAt) {
    }

    @Transactional(readOnly = true)
    public String buildHistoryContext(String userId, String sessionId) {
        List<ConversationMemory> history = loadRecentHistory(userId, sessionId);
        if (history.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("Short-term conversation (last turns only):\n");
        for (ConversationMemory memory : history) {
            sb.append(memory.getRole()).append(": ").append(truncate(memory.getContent(), MAX_CONTENT)).append("\n");
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

    private String truncate(String content, int max) {
        if (content == null) {
            return "";
        }
        String trimmed = content.trim();
        if (trimmed.length() <= max) {
            return trimmed;
        }
        return trimmed.substring(0, max) + "...";
    }
}
