package org.aiincubator.ilmai.plan;

import org.aiincubator.ilmai.common.CurrentUser;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlanApi {

    LearningPlanDto savePlan(CurrentUser currentUser, String goal, LocalDate targetDate, List<PlanStepInput> steps);

    Optional<LearningPlanDto> getActivePlan(CurrentUser currentUser);

    Optional<LearningPlanDto> getActivePlanForUser(UUID userId);

    Optional<LearningPlanDto> completeStep(CurrentUser currentUser, int dayIndex);
}
