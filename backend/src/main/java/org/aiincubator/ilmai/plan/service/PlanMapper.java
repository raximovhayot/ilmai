package org.aiincubator.ilmai.plan.service;

import org.aiincubator.ilmai.materials.MaterialsApi;
import org.aiincubator.ilmai.plan.domain.LearningPlan;
import org.aiincubator.ilmai.plan.domain.PlanStep;
import org.aiincubator.ilmai.plan.payload.LearningPlanResponse;
import org.aiincubator.ilmai.plan.payload.PlanMaterialRef;
import org.aiincubator.ilmai.plan.payload.PlanStepResponse;
import org.aiincubator.ilmai.plan.payload.StepLessonResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public abstract class PlanMapper {

    protected MaterialsApi materialsApi;

    @Autowired
    public void setMaterialsApi(MaterialsApi materialsApi) {
        this.materialsApi = materialsApi;
    }

    @Mapping(target = "daysTotal", expression = "java(countDays(plan))")
    @Mapping(target = "daysCompleted", expression = "java(countDone(plan))")
    public abstract LearningPlanResponse toResponse(LearningPlan plan);

    @Mapping(target = "materials", expression = "java(resolveMaterials(step.getMaterialIds()))")
    @Mapping(target = "hasLesson", expression = "java(hasLesson(step))")
    public abstract PlanStepResponse toResponse(PlanStep step);

    @Mapping(target = "content", source = "lessonContent")
    @Mapping(target = "citations", source = "lessonCitations")
    @Mapping(target = "generatedAt", source = "lessonGeneratedAt")
    public abstract StepLessonResponse toLesson(PlanStep step);

    protected boolean hasLesson(PlanStep step) {
        return step.getLessonContent() != null && !step.getLessonContent().isBlank();
    }

    protected int countDays(LearningPlan plan) {
        return (int) plan.getSteps().stream()
                .map(PlanStep::getDayIndex)
                .distinct()
                .count();
    }

    protected int countDone(LearningPlan plan) {
        Map<Integer, List<PlanStep>> stepsByDay = plan.getSteps().stream()
                .collect(Collectors.groupingBy(PlanStep::getDayIndex));
        return (int) stepsByDay.values().stream()
                .filter(daySteps -> daySteps.stream().allMatch(PlanStep::isDone))
                .count();
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
