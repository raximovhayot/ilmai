package org.aiincubator.ilmai.plan.service;

import lombok.RequiredArgsConstructor;
import org.aiincubator.ilmai.common.CurrentUser;
import org.aiincubator.ilmai.plan.LearningPlanDto;
import org.aiincubator.ilmai.plan.PlanApi;
import org.aiincubator.ilmai.plan.PlanStepInput;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DefaultPlanApi implements PlanApi {

    private final PlanService planService;
    private final PlanApiMapper planApiMapper;

    @Override
    @Transactional
    public LearningPlanDto savePlan(CurrentUser currentUser, String goal, LocalDate targetDate,
                                    List<PlanStepInput> steps) {
        return planApiMapper.toDto(
                planService.replaceActivePlan(currentUser.getUserId(), goal, targetDate, steps));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<LearningPlanDto> getActivePlan(CurrentUser currentUser) {
        return planService.findActivePlan(currentUser.getUserId()).map(planApiMapper::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<LearningPlanDto> getActivePlanForUser(UUID userId) {
        return planService.findActivePlan(userId).map(planApiMapper::toDto);
    }

    @Override
    @Transactional
    public Optional<LearningPlanDto> completeStep(CurrentUser currentUser, int dayIndex) {
        return planService.completeStep(currentUser.getUserId(), dayIndex).map(planApiMapper::toDto);
    }
}
