package org.aiincubator.ilmai.plan.service;

import org.aiincubator.ilmai.common.i18n.SupportedLocale;
import org.aiincubator.ilmai.plan.PlanStatus;
import org.aiincubator.ilmai.plan.domain.LearningPlan;
import org.aiincubator.ilmai.plan.domain.LearningPlanRepository;
import org.aiincubator.ilmai.plan.domain.PlanStep;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlanServiceTest {

    @Mock LearningPlanRepository learningPlanRepository;
    @Mock ProfilesApi profilesApi;
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

    private PlanStep step(String scheduledDate, boolean done) {
        PlanStep step = new PlanStep();
        step.setScheduledDate(scheduledDate == null ? null : LocalDate.parse(scheduledDate));
        step.setDone(done);
        return step;
    }

    private ProfileDto profile(String timezone) {
        return new ProfileDto(userId, SupportedLocale.EN, timezone, null, null, null, null, 0, 0, 0, null);
    }
}
