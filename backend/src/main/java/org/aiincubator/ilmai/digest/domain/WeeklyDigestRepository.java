package org.aiincubator.ilmai.digest.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface WeeklyDigestRepository extends JpaRepository<WeeklyDigest, UUID> {

    boolean existsByUserIdAndIsoWeek(UUID userId, String isoWeek);

    Optional<WeeklyDigest> findFirstByUserIdOrderByGeneratedAtDesc(UUID userId);
}
