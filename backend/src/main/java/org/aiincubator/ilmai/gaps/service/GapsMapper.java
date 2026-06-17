package org.aiincubator.ilmai.gaps.service;

import org.aiincubator.ilmai.gaps.domain.KnowledgeGap;
import org.aiincubator.ilmai.gaps.payload.GapItem;
import org.aiincubator.ilmai.materials.MaterialDto;
import org.aiincubator.ilmai.materials.MaterialsApi;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

@Mapper(componentModel = "spring")
public abstract class GapsMapper {

    protected MaterialsApi materialsApi;

    @Autowired
    public void setMaterialsApi(MaterialsApi materialsApi) {
        this.materialsApi = materialsApi;
    }

    @Mapping(target = "suggestedMaterialId", source = "suggestedMaterialId")
    @Mapping(target = "suggestedMaterialName", expression = "java(resolveMaterialName(gap.getSuggestedMaterialId()))")
    @Mapping(target = "accuracy", expression = "java(accuracyOf(gap))")
    public abstract GapItem toResponse(KnowledgeGap gap);

    protected double accuracyOf(KnowledgeGap gap) {
        int total = gap.getHitCount() + gap.getMissCount();
        return total == 0 ? 0.0 : (double) gap.getHitCount() / total;
    }

    protected String resolveMaterialName(UUID materialId) {
        if (materialId == null) {
            return null;
        }
        return materialsApi.findById(materialId).map(MaterialDto::getTitle).orElse(null);
    }
}
