package org.aiincubator.ilmai.notifications.service;

import org.aiincubator.ilmai.common.i18n.SupportedLocale;
import org.aiincubator.ilmai.notifications.NotificationsApi;
import org.aiincubator.ilmai.notifications.OutboxChannel;
import org.aiincubator.ilmai.notifications.OutboxMessageRequest;
import org.aiincubator.ilmai.notifications.OutboxMessageType;
import org.aiincubator.ilmai.plan.LearningPlanDto;
import org.aiincubator.ilmai.plan.PlanActivity;
import org.aiincubator.ilmai.plan.PlanApi;
import org.aiincubator.ilmai.plan.PlanStatus;
import org.aiincubator.ilmai.plan.PlanStepDto;
import org.aiincubator.ilmai.profiles.ProfileDto;
import org.aiincubator.ilmai.profiles.ProfilesApi;
import org.aiincubator.ilmai.streaks.StreakDto;
import org.aiincubator.ilmai.streaks.StreaksApi;
import org.aiincubator.ilmai.agent.UserMemoryApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock ProfilesApi profilesApi;
    @Mock StreaksApi streaksApi;
    @Mock PlanApi planApi;
    @Mock NotificationsApi notificationsApi;
    @Mock NotificationComposer composer;
    @Mock UserMemoryApi userMemoryApi;

    private final UUID userId = UUID.randomUUID();
    private final Clock clock = Clock.fixed(Instant.parse("2026-06-01T09:00:00Z"), ZoneOffset.UTC);

    private NotificationService service;

    @BeforeEach
    void setUp() {
        service = new NotificationService(profilesApi, streaksApi, planApi, notificationsApi, composer,
                userMemoryApi, clock, 8);
    }

    @Test
    void nudgeBrokenStreak_enqueuesNudgeScheduledNextMorningInUserZone() {
        when(profilesApi.find(userId)).thenReturn(Optional.of(profile("Asia/Tashkent", null)));
        when(composer.composeBrokenStreak(eq(3), any())).thenReturn("broken body");

        service.nudgeBrokenStreak(userId, LocalDate.parse("2026-06-01"), 3);

        OutboxMessageRequest req = captureEnqueue();
        assertThat(req.getUserId()).isEqualTo(userId);
        assertThat(req.getChannel()).isEqualTo(OutboxChannel.TELEGRAM);
        assertThat(req.getType()).isEqualTo(OutboxMessageType.BROKEN_STREAK_NUDGE);
        assertThat(req.getBody()).isEqualTo("broken body");
        assertThat(req.getDedupeKey()).isEqualTo("streak-broken:" + userId + ":2026-06-01");
        assertThat(req.getScheduledFor())
                .isEqualTo(OffsetDateTime.parse("2026-06-02T08:00:00+05:00"));
    }

    @Test
    void celebrateMilestone_enqueuesMilestoneNow() {
        when(profilesApi.find(userId)).thenReturn(Optional.of(profile("UTC", null)));
        when(composer.composeMilestone(eq(7), any())).thenReturn("milestone body");

        service.celebrateMilestone(userId, 7);

        OutboxMessageRequest req = captureEnqueue();
        assertThat(req.getType()).isEqualTo(OutboxMessageType.STREAK_MILESTONE);
        assertThat(req.getBody()).isEqualTo("milestone body");
        assertThat(req.getDedupeKey()).isEqualTo("streak-milestone:" + userId + ":7");
        assertThat(req.getScheduledFor()).isEqualTo(OffsetDateTime.parse("2026-06-01T09:00:00Z"));
    }

    @Test
    void sendReminderIfDue_dueHour_enqueuesDailyReminderWithTodaysStep() {
        when(profilesApi.find(userId)).thenReturn(Optional.of(profile("UTC", LocalTime.of(9, 0))));
        when(streaksApi.getStreak(userId))
                .thenReturn(new StreakDto(userId, 5, 5, LocalDate.parse("2026-05-31"), null));
        when(planApi.getActivePlanForUser(userId)).thenReturn(Optional.of(plan(
                step(1, "2026-06-01", "Read chapter 3", false))));
        when(composer.composeDailyReminder(eq(5), eq("Read chapter 3"), anyInt(), any())).thenReturn("reminder body");

        assertThat(service.sendReminderIfDue(userId)).isTrue();

        OutboxMessageRequest req = captureEnqueue();
        assertThat(req.getType()).isEqualTo(OutboxMessageType.DAILY_REMINDER);
        assertThat(req.getBody()).isEqualTo("reminder body");
        assertThat(req.getDedupeKey()).isEqualTo("reminder:" + userId + ":2026-06-01");
    }

    @Test
    void sendReminderIfDue_usesUserTimezoneForHourGate() {
        when(profilesApi.find(userId)).thenReturn(Optional.of(profile("Asia/Tashkent", LocalTime.of(14, 0))));
        when(streaksApi.getStreak(userId)).thenReturn(new StreakDto(userId, 0, 0, null, null));
        when(planApi.getActivePlanForUser(userId)).thenReturn(Optional.empty());
        when(composer.composeDailyReminder(eq(0), isNull(), anyInt(), any())).thenReturn("no streak body");

        assertThat(service.sendReminderIfDue(userId)).isTrue();

        OutboxMessageRequest req = captureEnqueue();
        assertThat(req.getDedupeKey()).isEqualTo("reminder:" + userId + ":2026-06-01");
    }

    @Test
    void sendReminderIfDue_wrongHour_doesNothing() {
        when(profilesApi.find(userId)).thenReturn(Optional.of(profile("UTC", LocalTime.of(7, 0))));

        assertThat(service.sendReminderIfDue(userId)).isFalse();

        verifyNoInteractions(notificationsApi);
    }

    @Test
    void sendReminderIfDue_noProfileOrNoReminder_doesNothing() {
        when(profilesApi.find(userId)).thenReturn(Optional.empty());

        assertThat(service.sendReminderIfDue(userId)).isFalse();

        verifyNoInteractions(notificationsApi);
    }

    @Test
    void sendDueReminders_onlyEnqueuesUsersDueAtTheirHour() {
        UUID other = UUID.randomUUID();
        when(profilesApi.findUserIdsWithDailyReminder()).thenReturn(List.of(userId, other));
        when(profilesApi.find(userId)).thenReturn(Optional.of(profile("UTC", LocalTime.of(9, 0))));
        when(profilesApi.find(other)).thenReturn(Optional.of(profile("UTC", LocalTime.of(7, 0))));
        when(streaksApi.getStreak(userId)).thenReturn(new StreakDto(userId, 0, 0, null, null));
        when(planApi.getActivePlanForUser(userId)).thenReturn(Optional.empty());
        when(composer.composeDailyReminder(anyInt(), any(), anyInt(), any())).thenReturn("body");

        assertThat(service.sendDueReminders()).isEqualTo(1);

        verify(notificationsApi, times(1)).enqueue(any());
    }

    private OutboxMessageRequest captureEnqueue() {
        ArgumentCaptor<OutboxMessageRequest> captor = ArgumentCaptor.forClass(OutboxMessageRequest.class);
        verify(notificationsApi).enqueue(captor.capture());
        return captor.getValue();
    }

    private ProfileDto profile(String timezone, LocalTime reminder) {
        return new ProfileDto(userId, SupportedLocale.EN, timezone, reminder, 0, 0, 0, null);
    }

    private LearningPlanDto plan(PlanStepDto... steps) {
        return new LearningPlanDto(UUID.randomUUID(), "goal", LocalDate.parse("2026-07-01"),
                PlanStatus.ACTIVE, OffsetDateTime.parse("2026-05-01T00:00:00Z"), List.of(steps), false);
    }

    private PlanStepDto step(int dayIndex, String date, String title, boolean done) {
        return new PlanStepDto(UUID.randomUUID(), dayIndex, LocalDate.parse(date), title,
                PlanActivity.READ, List.of(), null, done);
    }
}
