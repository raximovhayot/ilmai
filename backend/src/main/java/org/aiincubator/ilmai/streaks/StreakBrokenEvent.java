package org.aiincubator.ilmai.streaks;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class StreakBrokenEvent {

    private final UUID userId;
    private final LocalDate brokenDate;
    private final int brokenStreakLength;
}
