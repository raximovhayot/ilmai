package org.aiincubator.ilmai.rooms.service;

import org.aiincubator.ilmai.rooms.domain.Room;
import org.aiincubator.ilmai.rooms.payload.RoomResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface RoomMapper {

    @Mapping(target = "id", source = "id")
    RoomResponse toResponse(Room room);
}
