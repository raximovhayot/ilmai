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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompleteStepToolTest {

    private final UUID userA = UUID.randomUUID();

    @Mock PlanApi planApi;
    @Mock PlanViewFactory planViewFactory;

    @InjectMocks CompleteStepTool tool;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void completeStepReturnsNotReadyWhenNoActivePlan() {
        when(planApi.completeStep(any(), eq(2))).thenReturn(Optional.empty());
        authenticate(userA);

        PlanView view = tool.completeStep(2, new ToolContext(Map.of(AgentToolContext.CURRENT_USER_KEY, new CurrentUser(userA))));

        assertThat(view.isHasPlan()).isFalse();
        assertThat(view.getSteps()).isEmpty();
        verifyNoInteractions(planViewFactory);
    }

    @Test
    void completeStepMapsTheUpdatedPlanForTheContextUser() {
        LearningPlanDto plan = planWith(step(1, false), step(2, true));
        when(planApi.completeStep(any(), eq(2))).thenReturn(Optional.of(plan));
        PlanView expected = new PlanView(true, "IELTS", null, List.of(), false);
        when(planViewFactory.toView(eq(userA), eq(plan), anyList())).thenReturn(expected);
        authenticate(userA);

        PlanView view = tool.completeStep(2, new ToolContext(Map.of(AgentToolContext.CURRENT_USER_KEY, new CurrentUser(userA))));

        assertThat(view).isSameAs(expected);
        assertThat(captureSteps()).isEqualTo(plan.getSteps());
    }

    @Test
    void completeStepFailsWhenSecurityContextIsAnonymous() {
        assertThatThrownBy(() -> tool.completeStep(1, null)).isInstanceOf(IllegalStateException.class);
    }

    private List<PlanStepDto> captureSteps() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PlanStepDto>> captor = ArgumentCaptor.forClass(List.class);
        verify(planViewFactory).toView(eq(userA), any(), captor.capture());
        return captor.getValue();
    }

    private LearningPlanDto planWith(PlanStepDto... steps) {
        return new LearningPlanDto(UUID.randomUUID(), "IELTS", null,
                PlanStatus.ACTIVE, OffsetDateTime.now(), List.of(steps), false);
    }

    private PlanStepDto step(int day, boolean done) {
        return new PlanStepDto(UUID.randomUUID(), day, LocalDate.now(), "step " + day,
                PlanActivity.READ, List.of(), null, done);
    }

    private void authenticate(UUID userId) {
        TestingAuthenticationToken auth = new TestingAuthenticationToken(new CurrentUser(userId), null);
        auth.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
