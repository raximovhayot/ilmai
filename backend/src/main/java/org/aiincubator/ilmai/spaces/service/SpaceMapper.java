package org.aiincubator.ilmai.spaces.service;

import org.aiincubator.ilmai.spaces.domain.Space;
import org.aiincubator.ilmai.spaces.payload.SpaceResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface SpaceMapper {

    @Mapping(target = "id", source = "id")
    SpaceResponse toResponse(Space space);
}
