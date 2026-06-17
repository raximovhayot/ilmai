package org.aiincubator.ilmai.agent.usermemory.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReviewQueueRepository extends JpaRepository<ReviewQueueEntry, UUID> {

    Optional<ReviewQueueEntry> findByUserIdAndConcept(UUID userId, String concept);

    List<ReviewQueueEntry> findByUserIdOrderByNextReviewAtAsc(UUID userId);

    List<ReviewQueueEntry> findByUserIdAndStatusAndNextReviewAtLessThanEqualOrderByNextReviewAtAsc(
            UUID userId, ReviewStatus status, OffsetDateTime asOf);

    long countByUserIdAndStatusAndNextReviewAtLessThanEqual(
            UUID userId, ReviewStatus status, OffsetDateTime asOf);
}
