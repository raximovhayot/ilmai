package org.aiincubator.ilmai.agent.service;

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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlanBuilderTest {

    private final UUID userA = UUID.randomUUID();

    @Mock ProfilesApi profilesApi;
    @Mock MaterialsApi materialsApi;
    @Mock GapsApi gapsApi;
    @Mock Planner planner;
    @Mock PlanApi planApi;
    @Mock QuotaService quotaService;

    @InjectMocks PlanBuilder planBuilder;

    @Test
    void returnsEmptyWhenNoMaterialsAndNeverCallsPlannerOrQuota() {
        when(materialsApi.findReadyForUser(userA)).thenReturn(List.of());

        Optional<LearningPlanDto> result = planBuilder.build(userA, null, "en");

        assertThat(result).isEmpty();
        verify(planner, never()).plan(any(), any(), any());
        verify(quotaService, never()).reserve(any(), anyInt());
        verify(planApi, never()).savePlan(any(), any(), any(), anyList());
    }

    @Test
    void buildsSavesAndCommitsQuotaOnHappyPath() {
        MaterialDto material = readyMaterial();
        when(materialsApi.findReadyForUser(userA)).thenReturn(List.of(material));
        when(planner.isAvailable()).thenReturn(true);
        when(profilesApi.find(userA)).thenReturn(Optional.of(new ProfileDto(
                userA, SupportedLocale.EN, "Asia/Tashkent", "IELTS", null, null, 30, 0, 0, 0, null)));
        when(gapsApi.refreshAndGet(any())).thenReturn(Optional.empty());
        when(quotaService.canSpend(userA, PlanBuilder.PLAN_BUILD_ESTIMATE_ILM_TOKENS)).thenReturn(true);
        IlmTokenReservation reservation = mock(IlmTokenReservation.class);
        when(quotaService.reserve(userA, PlanBuilder.PLAN_BUILD_ESTIMATE_ILM_TOKENS)).thenReturn(reservation);

        PlanStepInput stepInput = new PlanStepInput(1, LocalDate.now(), "Read Notes",
                PlanActivity.READ, List.of(material.getId()), "start here");
        when(planner.plan(any(), any(), any())).thenReturn(new PlanDraft(List.of(stepInput), 5));
        LearningPlanDto saved = new LearningPlanDto(UUID.randomUUID(), "IELTS", null,
                PlanStatus.ACTIVE, OffsetDateTime.now(), List.of(), false);
        when(planApi.savePlan(any(), eq("IELTS"), isNull(), anyList())).thenReturn(saved);

        Optional<LearningPlanDto> result = planBuilder.build(userA, 7, "en");

        assertThat(result).containsSame(saved);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PlanStepInput>> stepsCaptor = ArgumentCaptor.forClass(List.class);
        verify(planApi).savePlan(any(), eq("IELTS"), isNull(), stepsCaptor.capture());
        assertThat(stepsCaptor.getValue()).containsExactly(stepInput);
        verify(quotaService).commit(reservation, 5);
        verify(quotaService, never()).refund(any());
    }

    @Test
    void shortCircuitsWhenQuotaExhausted() {
        MaterialDto material = readyMaterial();
        when(materialsApi.findReadyForUser(userA)).thenReturn(List.of(material));
        when(planner.isAvailable()).thenReturn(true);
        when(profilesApi.find(userA)).thenReturn(Optional.empty());
        when(gapsApi.refreshAndGet(any())).thenReturn(Optional.empty());
        when(quotaService.canSpend(userA, PlanBuilder.PLAN_BUILD_ESTIMATE_ILM_TOKENS)).thenReturn(false);

        Optional<LearningPlanDto> result = planBuilder.build(userA, null, "en");

        assertThat(result).isEmpty();
        verify(planner, never()).plan(any(), any(), any());
        verify(quotaService, never()).reserve(any(), anyInt());
        verify(planApi, never()).savePlan(any(), any(), any(), anyList());
    }

    @Test
    void refundsReservationWhenPlannerReturnsNoDraft() {
        MaterialDto material = readyMaterial();
        when(materialsApi.findReadyForUser(userA)).thenReturn(List.of(material));
        when(planner.isAvailable()).thenReturn(true);
        when(profilesApi.find(userA)).thenReturn(Optional.empty());
        when(gapsApi.refreshAndGet(any())).thenReturn(Optional.empty());
        when(quotaService.canSpend(userA, PlanBuilder.PLAN_BUILD_ESTIMATE_ILM_TOKENS)).thenReturn(true);
        IlmTokenReservation reservation = mock(IlmTokenReservation.class);
        when(quotaService.reserve(userA, PlanBuilder.PLAN_BUILD_ESTIMATE_ILM_TOKENS)).thenReturn(reservation);
        when(planner.plan(any(), any(), any())).thenReturn(null);

        Optional<LearningPlanDto> result = planBuilder.build(userA, null, "en");

        assertThat(result).isEmpty();
        verify(quotaService).refund(reservation);
        verify(quotaService, never()).commit(any(), anyInt());
        verify(planApi, never()).savePlan(any(), any(), any(), anyList());
    }

    private MaterialDto readyMaterial() {
        return new MaterialDto(UUID.randomUUID(), null, null, "Notes",
                "application/pdf", 10L, MaterialStatus.READY, 0, null, null);
    }
}
