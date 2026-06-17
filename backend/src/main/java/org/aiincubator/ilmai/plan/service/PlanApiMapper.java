package org.aiincubator.ilmai.plan.service;

import org.aiincubator.ilmai.plan.LearningPlanDto;
import org.aiincubator.ilmai.plan.PlanStepDto;
import org.aiincubator.ilmai.plan.domain.LearningPlan;
import org.aiincubator.ilmai.plan.domain.PlanStep;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface PlanApiMapper {

    LearningPlanDto toDto(LearningPlan plan);

    PlanStepDto toDto(PlanStep step);

    List<PlanStepDto> toStepDtos(List<PlanStep> steps);
}
