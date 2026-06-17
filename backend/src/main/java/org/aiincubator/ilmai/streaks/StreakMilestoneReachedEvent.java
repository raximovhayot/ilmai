package org.aiincubator.ilmai.streaks;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor
public class StreakMilestoneReachedEvent {

    private final UUID userId;
    private final int milestone;
}
