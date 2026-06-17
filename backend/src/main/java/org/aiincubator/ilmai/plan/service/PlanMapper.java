package org.aiincubator.ilmai.plan.service;

import org.aiincubator.ilmai.materials.MaterialsApi;
import org.aiincubator.ilmai.plan.domain.LearningPlan;
import org.aiincubator.ilmai.plan.domain.PlanStep;
import org.aiincubator.ilmai.plan.payload.LearningPlanResponse;
import org.aiincubator.ilmai.plan.payload.PlanMaterialRef;
import org.aiincubator.ilmai.plan.payload.PlanStepResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Mapper(componentModel = "spring")
public abstract class PlanMapper {

    protected MaterialsApi materialsApi;

    @Autowired
    public void setMaterialsApi(MaterialsApi materialsApi) {
        this.materialsApi = materialsApi;
    }

    @Mapping(target = "daysTotal", expression = "java(plan.getSteps().size())")
    @Mapping(target = "daysCompleted", expression = "java(countDone(plan))")
    public abstract LearningPlanResponse toResponse(LearningPlan plan);

    @Mapping(target = "materials", expression = "java(resolveMaterials(step.getMaterialIds()))")
    public abstract PlanStepResponse toResponse(PlanStep step);

    protected int countDone(LearningPlan plan) {
        return (int) plan.getSteps().stream().filter(PlanStep::isDone).count();
    }

    protected List<PlanMaterialRef> resolveMaterials(List<UUID> materialIds) {
        if (materialIds == null || materialIds.isEmpty()) {
            return List.of();
        }
        List<PlanMaterialRef> refs = new ArrayList<>();
        for (UUID materialId : materialIds) {
            materialsApi.findById(materialId).ifPresent(material ->
                    refs.add(new PlanMaterialRef(material.getId(), material.getTitle(), material.getTopicId())));
        }
        return refs;
    }
}
