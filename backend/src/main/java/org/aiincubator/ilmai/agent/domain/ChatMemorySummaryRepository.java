package org.aiincubator.ilmai.agent.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ChatMemorySummaryRepository extends JpaRepository<ChatMemorySummary, UUID> {

    Optional<ChatMemorySummary> findBySessionId(UUID sessionId);
}
