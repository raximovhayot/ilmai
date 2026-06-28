package org.aiincubator.ilmai.rooms;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class RoomGoalDto {

    private final UUID roomId;
    private final String goal;
    private final LocalDate targetDate;
    private final Integer dailyStudyMinutes;
}
