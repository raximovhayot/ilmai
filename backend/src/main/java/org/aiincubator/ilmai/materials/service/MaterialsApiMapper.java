package org.aiincubator.ilmai.materials.service;

import org.aiincubator.ilmai.materials.MaterialDto;
import org.aiincubator.ilmai.materials.TopicDto;
import org.aiincubator.ilmai.materials.domain.Material;
import org.aiincubator.ilmai.materials.domain.Topic;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface MaterialsApiMapper {

    @Mapping(target = "topicId", source = "topic.id")
    @Mapping(target = "spaceId", source = "spaceId")
    MaterialDto toDto(Material material);

    TopicDto toDto(Topic topic);
}
