package org.aiincubator.ilmai.spaces.service;

import org.aiincubator.ilmai.spaces.SpaceDto;
import org.aiincubator.ilmai.spaces.domain.Space;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface SpacesApiMapper {

    SpaceDto toSpaceDto(Space space);
}
