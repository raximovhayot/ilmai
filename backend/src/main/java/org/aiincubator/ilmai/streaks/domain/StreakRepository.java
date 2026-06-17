package org.aiincubator.ilmai.streaks.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface StreakRepository extends JpaRepository<Streak, UUID> {
}
