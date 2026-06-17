package org.aiincubator.ilmai.quiz.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface QuizSessionRepository extends JpaRepository<QuizSession, UUID> {

    Optional<QuizSession> findByIdAndUserId(UUID id, UUID userId);

    List<QuizSession> findAllByUserIdOrderByCreatedAtDesc(UUID userId);

    long countByUserIdAndStartedAtAfter(UUID userId, OffsetDateTime since);
}
