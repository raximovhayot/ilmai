package org.aiincubator.ilmai.materials.service;

import org.aiincubator.ilmai.materials.domain.Topic;
import org.aiincubator.ilmai.materials.payload.TopicResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface TopicMapper {

    @Mapping(target = "spaceId", source = "roomId")
    TopicResponse toResponse(Topic topic);
}
