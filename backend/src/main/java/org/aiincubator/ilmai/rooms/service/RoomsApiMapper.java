package org.aiincubator.ilmai.rooms.service;

import org.aiincubator.ilmai.rooms.RoomDto;
import org.aiincubator.ilmai.rooms.RoomGoalDto;
import org.aiincubator.ilmai.rooms.domain.Room;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface RoomsApiMapper {

    RoomDto toRoomDto(Room room);

    @Mapping(target = "roomId", source = "id")
    RoomGoalDto toRoomGoalDto(Room room);
}
