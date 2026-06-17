package org.aiincubator.ilmai.materials.service;

import org.aiincubator.ilmai.materials.domain.Topic;
import org.aiincubator.ilmai.materials.payload.TopicResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface TopicMapper {

    TopicResponse toResponse(Topic topic);
}
