package org.aiincubator.ilmai.quiz.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface QuizQuestionRepository extends JpaRepository<QuizQuestion, UUID> {

    Optional<QuizQuestion> findByIdAndSessionUserId(UUID id, UUID userId);

    List<QuizQuestion> findAllBySessionUserIdAndIsCorrectFalse(UUID userId);

    long countBySessionUserIdAndIsCorrectIsNotNullAndUpdatedAtAfter(UUID userId, OffsetDateTime since);

    long countBySessionUserIdAndIsCorrectIsTrueAndUpdatedAtAfter(UUID userId, OffsetDateTime since);
}
