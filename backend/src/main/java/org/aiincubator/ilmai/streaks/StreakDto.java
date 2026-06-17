package org.aiincubator.ilmai.streaks;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@AllArgsConstructor
public final class StreakDto {

    private final UUID userId;
    private final int streakCurrent;
    private final int streakLongest;
    private final LocalDate streakLastDay;
    private final LocalDate streakBrokenAt;
}
