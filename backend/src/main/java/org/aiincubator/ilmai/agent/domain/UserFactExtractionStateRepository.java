package org.aiincubator.ilmai.agent.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserFactExtractionStateRepository extends JpaRepository<UserFactExtractionState, UUID> {

    Optional<UserFactExtractionState> findBySessionId(UUID sessionId);
}
