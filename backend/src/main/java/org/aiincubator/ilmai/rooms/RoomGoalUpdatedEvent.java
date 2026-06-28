package org.aiincubator.ilmai.rooms;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor
public class RoomGoalUpdatedEvent {

    private final UUID userId;
    private final UUID roomId;
}
