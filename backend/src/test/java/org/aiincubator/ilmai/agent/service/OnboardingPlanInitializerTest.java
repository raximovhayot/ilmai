package org.aiincubator.ilmai.agent.service;

import org.aiincubator.ilmai.plan.LearningPlanDto;
import org.aiincubator.ilmai.plan.PlanApi;
import org.aiincubator.ilmai.plan.PlanStatus;
import org.aiincubator.ilmai.profiles.OnboardingCompletedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OnboardingPlanInitializerTest {

    private final UUID userA = UUID.randomUUID();

    @Mock PlanBuilder planBuilder;
    @Mock PlanApi planApi;

    @InjectMocks OnboardingPlanInitializer initializer;

    @Test
    void buildsPlanWhenNoneExists() {
        when(planApi.getActivePlanForUser(userA)).thenReturn(Optional.empty());
        when(planBuilder.build(userA, null, null)).thenReturn(Optional.empty());

        initializer.onOnboardingCompleted(new OnboardingCompletedEvent(userA));

        verify(planBuilder).build(userA, null, null);
    }

    @Test
    void skipsBuildWhenActivePlanAlreadyExists() {
        LearningPlanDto existing = new LearningPlanDto(UUID.randomUUID(), "IELTS", null,
                PlanStatus.ACTIVE, OffsetDateTime.now(), List.of(), false);
        when(planApi.getActivePlanForUser(userA)).thenReturn(Optional.of(existing));

        initializer.onOnboardingCompleted(new OnboardingCompletedEvent(userA));

        verify(planBuilder, never()).build(any(), any(), any());
    }

    @Test
    void swallowsBuilderExceptions() {
        when(planApi.getActivePlanForUser(userA)).thenReturn(Optional.empty());
        when(planBuilder.build(eq(userA), isNull(), isNull())).thenThrow(new RuntimeException("boom"));

        initializer.onOnboardingCompleted(new OnboardingCompletedEvent(userA));

        verify(planBuilder).build(userA, null, null);
    }
}
