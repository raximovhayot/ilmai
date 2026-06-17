package org.aiincubator.ilmai.streaks.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface StreakActivityDayRepository extends JpaRepository<StreakActivityDay, UUID> {

    boolean existsByUserIdAndActivityDate(UUID userId, LocalDate activityDate);

    long countByUserId(UUID userId);

    long countByUserIdAndActivityDateGreaterThanEqual(UUID userId, LocalDate from);

    @Query("select distinct d.userId from StreakActivityDay d")
    List<UUID> findDistinctUserIds();
}
