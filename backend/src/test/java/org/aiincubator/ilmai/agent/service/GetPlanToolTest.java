package org.aiincubator.ilmai.agent.service;

import org.aiincubator.ilmai.common.CurrentUser;
import org.aiincubator.ilmai.plan.LearningPlanDto;
import org.aiincubator.ilmai.plan.PlanActivity;
import org.aiincubator.ilmai.plan.PlanApi;
import org.aiincubator.ilmai.plan.PlanStatus;
import org.aiincubator.ilmai.plan.PlanStepDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetPlanToolTest {

    private final UUID userA = UUID.randomUUID();

    @Mock PlanApi planApi;
    @Mock PlanViewFactory planViewFactory;

    @InjectMocks GetPlanTool tool;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getPlanReturnsNotReadyWhenNoActivePlan() {
        when(planApi.getActivePlan(any())).thenReturn(Optional.empty());
        authenticate(userA);

        PlanView view = tool.getPlan(new ToolContext(Map.of(AgentToolContext.CURRENT_USER_KEY, new CurrentUser(userA))));

        assertThat(view.isHasPlan()).isFalse();
        assertThat(view.getSteps()).isEmpty();
        verifyNoInteractions(planViewFactory);
    }

    @Test
    void getPlanMapsTheFullActivePlan() {
        LearningPlanDto plan = planWith(
                step(1, LocalDate.now(), false),
                step(2, LocalDate.now().plusDays(1), false));
        when(planApi.getActivePlan(any())).thenReturn(Optional.of(plan));
        PlanView expected = new PlanView(true, "IELTS", null, List.of(), false);
        when(planViewFactory.toView(eq(userA), eq(plan), anyList())).thenReturn(expected);
        authenticate(userA);

        PlanView view = tool.getPlan(new ToolContext(Map.of(AgentToolContext.CURRENT_USER_KEY, new CurrentUser(userA))));

        assertThat(view).isSameAs(expected);
        assertThat(captureSteps()).isEqualTo(plan.getSteps());
    }

    @Test
    void getTodaysTaskFiltersToStepsScheduledToday() {
        PlanStepDto today = step(1, LocalDate.now(), false);
        LearningPlanDto plan = planWith(today, step(2, LocalDate.now().plusDays(1), false));
        when(planApi.getActivePlan(any())).thenReturn(Optional.of(plan));
        when(planViewFactory.toView(eq(userA), eq(plan), anyList()))
                .thenReturn(new PlanView(true, null, null, List.of(), false));
        authenticate(userA);

        tool.getTodaysTask(new ToolContext(Map.of(AgentToolContext.CURRENT_USER_KEY, new CurrentUser(userA))));

        assertThat(captureSteps()).containsExactly(today);
    }

    @Test
    void getTodaysTaskFallsBackToFirstUnfinishedStepWhenNothingDueToday() {
        PlanStepDto doneStep = step(1, LocalDate.now().minusDays(2), true);
        PlanStepDto nextStep = step(2, LocalDate.now().plusDays(3), false);
        LearningPlanDto plan = planWith(doneStep, nextStep);
        when(planApi.getActivePlan(any())).thenReturn(Optional.of(plan));
        when(planViewFactory.toView(eq(userA), eq(plan), anyList()))
                .thenReturn(new PlanView(true, null, null, List.of(), false));
        authenticate(userA);

        tool.getTodaysTask(new ToolContext(Map.of(AgentToolContext.CURRENT_USER_KEY, new CurrentUser(userA))));

        assertThat(captureSteps()).containsExactly(nextStep);
    }

    @Test
    void getPlanFailsWhenSecurityContextIsAnonymous() {
        assertThatThrownBy(() -> tool.getPlan(null)).isInstanceOf(IllegalStateException.class);
    }

    private List<PlanStepDto> captureSteps() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PlanStepDto>> captor = ArgumentCaptor.forClass(List.class);
        org.mockito.Mockito.verify(planViewFactory).toView(eq(userA), any(), captor.capture());
        return captor.getValue();
    }

    private LearningPlanDto planWith(PlanStepDto... steps) {
        return new LearningPlanDto(UUID.randomUUID(), "IELTS", null,
                PlanStatus.ACTIVE, OffsetDateTime.now(), List.of(steps), false);
    }

    private PlanStepDto step(int day, LocalDate date, boolean done) {
        return new PlanStepDto(UUID.randomUUID(), day, date, "step " + day,
                PlanActivity.READ, List.of(), null, done);
    }

    private void authenticate(UUID userId) {
        TestingAuthenticationToken auth = new TestingAuthenticationToken(new CurrentUser(userId), null);
        auth.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
