package org.aiincubator.ilmai.rooms;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor
public class RoomDto {

    private final UUID id;
    private final UUID ownerId;
    private final String name;
    private final boolean personal;
}
