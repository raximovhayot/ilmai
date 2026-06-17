package org.aiincubator.ilmai.streaks;

import java.time.LocalDate;
import java.util.UUID;

public interface StreaksApi {

    StreakDto getStreak(UUID userId);

    int countActivityDays(UUID userId);

    int countActivityDaysSince(UUID userId, LocalDate from);
}
