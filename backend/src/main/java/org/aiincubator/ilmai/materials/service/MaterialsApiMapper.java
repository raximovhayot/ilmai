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
    @Mapping(target = "spaceId", source = "roomId")
    MaterialDto toDto(Material material);

    @Mapping(target = "spaceId", source = "roomId")
    TopicDto toDto(Topic topic);
}
