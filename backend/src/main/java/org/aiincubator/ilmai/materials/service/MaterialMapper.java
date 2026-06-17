package org.aiincubator.ilmai.materials.service;

import org.aiincubator.ilmai.materials.domain.Material;
import org.aiincubator.ilmai.materials.payload.MaterialResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface MaterialMapper {

    @Mapping(target = "topicId", source = "topic.id")
    @Mapping(target = "status", expression = "java(material.getStatus() != null ? material.getStatus().name() : null)")
    MaterialResponse toResponse(Material material);
}
