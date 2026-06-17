package org.aiincubator.ilmai.notifications.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OutboxMessageRepository extends JpaRepository<OutboxMessage, UUID> {

    Optional<OutboxMessage> findByDedupeKey(String dedupeKey);

    List<OutboxMessage> findBySentAtIsNullAndScheduledForLessThanEqualOrderByScheduledForAsc(OffsetDateTime asOf);
}
