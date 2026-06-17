package org.aiincubator.ilmai.agent.domain;

import org.aiincubator.ilmai.agent.ChatChannel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChatSessionRepository extends JpaRepository<ChatSession, UUID> {

    List<ChatSession> findAllByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<ChatSession> findFirstByUserIdAndChannelOrderByCreatedAtAsc(UUID userId, ChatChannel channel);
}
