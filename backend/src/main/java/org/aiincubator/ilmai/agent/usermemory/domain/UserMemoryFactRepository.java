package org.aiincubator.ilmai.agent.usermemory.domain;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface UserMemoryFactRepository extends JpaRepository<UserMemoryFact, UUID> {

    List<UserMemoryFact> findByUserIdOrderByCreatedAtDesc(UUID userId, Limit limit);
}
