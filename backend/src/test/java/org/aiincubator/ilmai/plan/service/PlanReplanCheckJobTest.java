package org.aiincubator.ilmai.plan.service;

import org.aiincubator.ilmai.plan.PlanStatus;
import org.aiincubator.ilmai.plan.domain.LearningPlanRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlanReplanCheckJobTest {

    @Mock LearningPlanRepository learningPlanRepository;
    @Mock PlanService planService;

    @Test
    void run_flagsEachDistinctActivePlanUserWithConfiguredThreshold() {
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        when(learningPlanRepository.findDistinctUserIdsByStatus(PlanStatus.ACTIVE))
                .thenReturn(List.of(userA, userB));
        PlanReplanCheckJob job = new PlanReplanCheckJob(learningPlanRepository, planService, 3);

        job.run();

        verify(planService).flagIfBehind(userA, 3);
        verify(planService).flagIfBehind(userB, 3);
    }

    @Test
    void run_noActivePlans_doesNothing() {
        when(learningPlanRepository.findDistinctUserIdsByStatus(PlanStatus.ACTIVE))
                .thenReturn(List.of());
        PlanReplanCheckJob job = new PlanReplanCheckJob(learningPlanRepository, planService, 3);

        job.run();

        verifyNoInteractions(planService);
    }
}
