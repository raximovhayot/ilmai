package org.aiincubator.ilmai.agent.service;

import org.aiincubator.ilmai.common.CurrentUser;
import org.aiincubator.ilmai.common.i18n.SupportedLocale;
import org.aiincubator.ilmai.common.quota.IlmTokenReservation;
import org.aiincubator.ilmai.common.quota.QuotaService;
import org.aiincubator.ilmai.gaps.GapsApi;
import org.aiincubator.ilmai.materials.MaterialDto;
import org.aiincubator.ilmai.materials.MaterialStatus;
import org.aiincubator.ilmai.materials.MaterialsApi;
import org.aiincubator.ilmai.plan.LearningPlanDto;
import org.aiincubator.ilmai.plan.PlanActivity;
import org.aiincubator.ilmai.plan.PlanApi;
import org.aiincubator.ilmai.plan.PlanStatus;
import org.aiincubator.ilmai.plan.PlanStepInput;
import org.aiincubator.ilmai.profiles.ProfileDto;
import org.aiincubator.ilmai.profiles.ProfilesApi;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BuildPlanToolTest {

    private final UUID userA = UUID.randomUUID();

    @Mock ProfilesApi profilesApi;
    @Mock MaterialsApi materialsApi;
    @Mock GapsApi gapsApi;
    @Mock Planner planner;
    @Mock PlanApi planApi;
    @Mock PlanViewFactory planViewFactory;
    @Mock QuotaService quotaService;

    @InjectMocks BuildPlanTool tool;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void buildPlanReturnsNotReadyWhenNoMaterialsAndNeverCallsPlannerOrQuota() {
        when(materialsApi.findReadyForUser(userA)).thenReturn(List.of());
        authenticate(userA);

        PlanView view = tool.buildPlan(null, "en", new ToolContext(Map.of(AgentToolContext.CURRENT_USER_KEY, new CurrentUser(userA))));

        assertThat(view.isHasPlan()).isFalse();
        assertThat(view.getSteps()).isEmpty();
        verify(planner, never()).plan(any(), any(), any());
        verifyNoInteractions(planApi, planViewFactory, quotaService);
    }

    @Test
    void buildPlanBuildsSavesAndCommitsQuotaOnHappyPath() {
        MaterialDto material = new MaterialDto(UUID.randomUUID(), null, null, "Notes",
                "application/pdf", 10L, MaterialStatus.READY, 0, null, null);
        when(materialsApi.findReadyForUser(userA)).thenReturn(List.of(material));
        when(planner.isAvailable()).thenReturn(true);
        when(profilesApi.find(userA)).thenReturn(Optional.of(new ProfileDto(
                userA, SupportedLocale.EN, "Asia/Tashkent", "IELTS", null, null, 30, 0, 0, 0, null)));
        when(gapsApi.refreshAndGet(any())).thenReturn(Optional.empty());
        when(quotaService.canSpend(userA, BuildPlanTool.PLAN_BUILD_ESTIMATE_ILM_TOKENS)).thenReturn(true);
        IlmTokenReservation reservation = mock(IlmTokenReservation.class);
        when(quotaService.reserve(userA, BuildPlanTool.PLAN_BUILD_ESTIMATE_ILM_TOKENS)).thenReturn(reservation);

        PlanStepInput stepInput = new PlanStepInput(1, LocalDate.now(), "Read Notes",
                PlanActivity.READ, List.of(material.getId()), "start here");
        when(planner.plan(any(), any(), any())).thenReturn(new PlanDraft(List.of(stepInput), 5));
        LearningPlanDto saved = new LearningPlanDto(UUID.randomUUID(), "IELTS", null,
                PlanStatus.ACTIVE, OffsetDateTime.now(), List.of(), false);
        when(planApi.savePlan(any(), eq("IELTS"), isNull(), anyList())).thenReturn(saved);
        PlanView expected = new PlanView(true, "IELTS", null, List.of(), false);
        when(planViewFactory.toView(eq(userA), eq(saved), anyList())).thenReturn(expected);

        authenticate(userA);
        PlanView view = tool.buildPlan(7, "en", new ToolContext(Map.of(AgentToolContext.CURRENT_USER_KEY, new CurrentUser(userA))));

        assertThat(view).isSameAs(expected);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PlanStepInput>> stepsCaptor = ArgumentCaptor.forClass(List.class);
        verify(planApi).savePlan(any(), eq("IELTS"), isNull(), stepsCaptor.capture());
        assertThat(stepsCaptor.getValue()).containsExactly(stepInput);
        verify(quotaService).commit(reservation, 5);
        verify(quotaService, never()).refund(any());
    }

    @Test
    void buildPlanShortCircuitsWhenQuotaExhausted() {
        MaterialDto material = new MaterialDto(UUID.randomUUID(), null, null, "Notes",
                "application/pdf", 10L, MaterialStatus.READY, 0, null, null);
        when(materialsApi.findReadyForUser(userA)).thenReturn(List.of(material));
        when(planner.isAvailable()).thenReturn(true);
        when(profilesApi.find(userA)).thenReturn(Optional.empty());
        when(gapsApi.refreshAndGet(any())).thenReturn(Optional.empty());
        when(quotaService.canSpend(userA, BuildPlanTool.PLAN_BUILD_ESTIMATE_ILM_TOKENS)).thenReturn(false);

        authenticate(userA);
        PlanView view = tool.buildPlan(null, "en", new ToolContext(Map.of(AgentToolContext.CURRENT_USER_KEY, new CurrentUser(userA))));

        assertThat(view.isHasPlan()).isFalse();
        verify(planner, never()).plan(any(), any(), any());
        verify(quotaService, never()).reserve(any(), anyInt());
        verify(planApi, never()).savePlan(any(), any(), any(), anyList());
    }

    private void authenticate(UUID userId) {
        TestingAuthenticationToken auth = new TestingAuthenticationToken(new CurrentUser(userId), null);
        auth.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
