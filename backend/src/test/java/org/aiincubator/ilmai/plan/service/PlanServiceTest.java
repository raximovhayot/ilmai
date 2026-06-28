package org.aiincubator.ilmai.plan.service;

import org.aiincubator.ilmai.common.CurrentUser;
import org.aiincubator.ilmai.common.i18n.SupportedLocale;
import org.aiincubator.ilmai.common.quota.IlmTokenReservation;
import org.aiincubator.ilmai.common.quota.QuotaService;
import org.aiincubator.ilmai.plan.PlanStatus;
import org.aiincubator.ilmai.plan.domain.LearningPlan;
import org.aiincubator.ilmai.plan.domain.LearningPlanRepository;
import org.aiincubator.ilmai.plan.domain.LessonCitation;
import org.aiincubator.ilmai.plan.domain.PlanStep;
import org.aiincubator.ilmai.plan.payload.StepLessonResponse;
import org.aiincubator.ilmai.profiles.ProfileDto;
import org.aiincubator.ilmai.profiles.ProfilesApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlanServiceTest {

    @Mock LearningPlanRepository learningPlanRepository;
    @Mock ProfilesApi profilesApi;
    @Mock PlanMapper planMapper;
    @Mock PlanLessonGenerator lessonGenerator;
    @Mock QuotaService quotaService;
    @Mock Clock clock;

    @InjectMocks PlanService service;

    private final UUID userId = UUID.randomUUID();

    @Test
    void flagIfBehind_noActivePlan_returnsFalseAndFlagsNothing() {
        when(learningPlanRepository.findFirstByUserIdAndStatusOrderByCreatedAtDesc(userId, PlanStatus.ACTIVE))
                .thenReturn(Optional.empty());

        assertThat(service.flagIfBehind(userId, 3)).isFalse();
    }

    @Test
    void flagIfBehind_overdueAtOrAboveThreshold_flagsReplanNeeded() {
        LearningPlan plan = planWith(
                step("2026-06-07", false),
                step("2026-06-08", false),
                step("2026-06-09", false));
        stubActivePlanAt("2026-06-10T06:00:00Z", "UTC", plan);

        boolean flagged = service.flagIfBehind(userId, 3);

        assertThat(flagged).isTrue();
        assertThat(plan.isReplanNeeded()).isTrue();
    }

    @Test
    void flagIfBehind_overdueBelowThreshold_doesNotFlag() {
        LearningPlan plan = planWith(
                step("2026-06-08", false),
                step("2026-06-09", false));
        stubActivePlanAt("2026-06-10T06:00:00Z", "UTC", plan);

        boolean flagged = service.flagIfBehind(userId, 3);

        assertThat(flagged).isFalse();
        assertThat(plan.isReplanNeeded()).isFalse();
    }

    @Test
    void flagIfBehind_doneOverdueSteps_doNotCount() {
        LearningPlan plan = planWith(
                step("2026-06-07", true),
                step("2026-06-08", true),
                step("2026-06-09", true));
        stubActivePlanAt("2026-06-10T06:00:00Z", "UTC", plan);

        boolean flagged = service.flagIfBehind(userId, 3);

        assertThat(flagged).isFalse();
        assertThat(plan.isReplanNeeded()).isFalse();
    }

    @Test
    void flagIfBehind_todayAndFutureSteps_doNotCount() {
        LearningPlan plan = planWith(
                step("2026-06-10", false),
                step("2026-06-11", false),
                step("2026-06-12", false));
        stubActivePlanAt("2026-06-10T06:00:00Z", "UTC", plan);

        boolean flagged = service.flagIfBehind(userId, 1);

        assertThat(flagged).isFalse();
        assertThat(plan.isReplanNeeded()).isFalse();
    }

    @Test
    void flagIfBehind_thresholdBelowOne_isClampedToOne() {
        LearningPlan plan = planWith(step("2026-06-09", false));
        stubActivePlanAt("2026-06-10T06:00:00Z", "UTC", plan);

        boolean flagged = service.flagIfBehind(userId, 0);

        assertThat(flagged).isTrue();
        assertThat(plan.isReplanNeeded()).isTrue();
    }

    @Test
    void generateLesson_noActivePlan_throwsNotFound() {
        when(learningPlanRepository.findFirstByUserIdAndStatusOrderByCreatedAtDesc(userId, PlanStatus.ACTIVE))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.generateLessonResponse(new CurrentUser(userId), 1, false))
                .isInstanceOf(PlanException.class)
                .extracting("reason").isEqualTo(PlanException.Reason.PLAN_NOT_FOUND);
    }

    @Test
    void generateLesson_existingLessonAndNotRegenerate_returnsCachedWithoutGenerating() {
        LearningPlan plan = planWith(lessonStep(1, "cached lesson"));
        when(learningPlanRepository.findFirstByUserIdAndStatusOrderByCreatedAtDesc(userId, PlanStatus.ACTIVE))
                .thenReturn(Optional.of(plan));
        StepLessonResponse mapped = new StepLessonResponse();
        when(planMapper.toLesson(plan.getSteps().get(0))).thenReturn(mapped);

        StepLessonResponse result = service.generateLessonResponse(new CurrentUser(userId), 1, false);

        assertThat(result).isSameAs(mapped);
        verifyNoInteractions(lessonGenerator);
        verifyNoInteractions(quotaService);
    }

    @Test
    void generateLesson_generatesAndPersistsLesson() {
        LearningPlan plan = planWith(plainStep(1, "Intro to vectors"));
        when(learningPlanRepository.findFirstByUserIdAndStatusOrderByCreatedAtDesc(userId, PlanStatus.ACTIVE))
                .thenReturn(Optional.of(plan));
        when(lessonGenerator.isAvailable()).thenReturn(true);
        when(quotaService.canSpend(userId, 8)).thenReturn(true);
        IlmTokenReservation reservation =
                new IlmTokenReservation(UUID.randomUUID(), userId, LocalDate.parse("2026-06-10"), 8);
        when(quotaService.reserve(userId, 8)).thenReturn(reservation);
        LessonDraft draft = new LessonDraft("Generated lesson [1]",
                List.of(new LessonCitation(UUID.randomUUID(), "Doc", 0, "snippet")));
        when(lessonGenerator.generate(eq(userId), any(), any(), any(), any())).thenReturn(draft);
        when(profilesApi.find(userId)).thenReturn(Optional.of(profile("UTC")));
        when(clock.instant()).thenReturn(Instant.parse("2026-06-10T06:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        when(planMapper.toLesson(plan.getSteps().get(0))).thenReturn(new StepLessonResponse());

        service.generateLessonResponse(new CurrentUser(userId), 1, false);

        PlanStep step = plan.getSteps().get(0);
        assertThat(step.getLessonContent()).isEqualTo("Generated lesson [1]");
        assertThat(step.getLessonCitations()).hasSize(1);
        assertThat(step.getLessonGeneratedAt()).isNotNull();
        verify(quotaService).commit(reservation, 8);
    }

    @Test
    void generateLesson_quotaExceededAndNoExistingLesson_throws() {
        LearningPlan plan = planWith(plainStep(1, "Intro to vectors"));
        when(learningPlanRepository.findFirstByUserIdAndStatusOrderByCreatedAtDesc(userId, PlanStatus.ACTIVE))
                .thenReturn(Optional.of(plan));
        when(lessonGenerator.isAvailable()).thenReturn(true);
        when(quotaService.canSpend(userId, 8)).thenReturn(false);

        assertThatThrownBy(() -> service.generateLessonResponse(new CurrentUser(userId), 1, false))
                .isInstanceOf(PlanException.class)
                .extracting("reason").isEqualTo(PlanException.Reason.PLAN_LESSON_QUOTA_EXCEEDED);
    }

    private void stubActivePlanAt(String nowUtc, String timezone, LearningPlan plan) {
        when(learningPlanRepository.findFirstByUserIdAndStatusOrderByCreatedAtDesc(userId, PlanStatus.ACTIVE))
                .thenReturn(Optional.of(plan));
        when(clock.instant()).thenReturn(Instant.parse(nowUtc));
        when(profilesApi.find(userId)).thenReturn(Optional.of(profile(timezone)));
    }

    private LearningPlan planWith(PlanStep... steps) {
        LearningPlan plan = new LearningPlan();
        plan.setUserId(userId);
        plan.setStatus(PlanStatus.ACTIVE);
        for (PlanStep step : steps) {
            plan.getSteps().add(step);
        }
        return plan;
    }

    private PlanStep plainStep(int dayIndex, String title) {
        PlanStep step = new PlanStep();
        step.setDayIndex(dayIndex);
        step.setTitle(title);
        return step;
    }

    private PlanStep lessonStep(int dayIndex, String content) {
        PlanStep step = plainStep(dayIndex, "Step " + dayIndex);
        step.setLessonContent(content);
        return step;
    }

    private PlanStep step(String scheduledDate, boolean done) {
        PlanStep step = new PlanStep();
        step.setScheduledDate(scheduledDate == null ? null : LocalDate.parse(scheduledDate));
        step.setDone(done);
        return step;
    }

    private ProfileDto profile(String timezone) {
        return new ProfileDto(userId, SupportedLocale.EN, timezone, null, 0, 0, 0, null);
    }
}
