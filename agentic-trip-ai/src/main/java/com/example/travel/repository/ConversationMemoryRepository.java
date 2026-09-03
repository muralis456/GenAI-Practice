package com.example.travel.repository;

import com.example.travel.entity.ConversationMemory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConversationMemoryRepository extends JpaRepository<ConversationMemory, Long> {
    List<ConversationMemory> findByUserIdAndSessionIdOrderByCreatedAtDesc(String userId, String sessionId, Pageable pageable);
    List<ConversationMemory> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);
    long countByUserId(String userId);
}
