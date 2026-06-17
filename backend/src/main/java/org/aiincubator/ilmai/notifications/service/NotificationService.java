package org.aiincubator.ilmai.notifications.service;

import lombok.extern.slf4j.Slf4j;
import org.aiincubator.ilmai.notifications.NotificationsApi;
import org.aiincubator.ilmai.notifications.OutboxChannel;
import org.aiincubator.ilmai.notifications.OutboxMessageRequest;
import org.aiincubator.ilmai.notifications.OutboxMessageType;
import org.aiincubator.ilmai.plan.LearningPlanDto;
import org.aiincubator.ilmai.plan.PlanApi;
import org.aiincubator.ilmai.plan.PlanStepDto;
import org.aiincubator.ilmai.profiles.ProfileDto;
import org.aiincubator.ilmai.profiles.ProfilesApi;
import org.aiincubator.ilmai.streaks.StreaksApi;
import org.aiincubator.ilmai.agent.UserMemoryApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class NotificationService {

    static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Tashkent");

    private final ProfilesApi profilesApi;
    private final StreaksApi streaksApi;
    private final PlanApi planApi;
    private final NotificationsApi notificationsApi;
    private final NotificationComposer composer;
    private final UserMemoryApi userMemoryApi;
    private final Clock clock;
    private final int brokenStreakNudgeHour;

    public NotificationService(ProfilesApi profilesApi,
                               StreaksApi streaksApi,
                               PlanApi planApi,
                               NotificationsApi notificationsApi,
                               NotificationComposer composer,
                               UserMemoryApi userMemoryApi,
                               Clock clock,
                               @Value("${notifications.broken-streak-nudge-hour:8}") int brokenStreakNudgeHour) {
        this.profilesApi = profilesApi;
        this.streaksApi = streaksApi;
        this.planApi = planApi;
        this.notificationsApi = notificationsApi;
        this.composer = composer;
        this.userMemoryApi = userMemoryApi;
        this.clock = clock;
        this.brokenStreakNudgeHour = brokenStreakNudgeHour;
    }

    public void nudgeBrokenStreak(UUID userId, LocalDate brokenDate, int brokenStreakLength) {
        ProfileDto profile = profilesApi.find(userId).orElse(null);
        OffsetDateTime scheduledFor = brokenDate.plusDays(1)
                .atTime(brokenStreakNudgeHour, 0)
                .atZone(zoneOf(profile))
                .toOffsetDateTime();
        String body = composer.composeBrokenStreak(brokenStreakLength, profile);
        enqueue(userId, OutboxMessageType.BROKEN_STREAK_NUDGE, body,
                "streak-broken:" + userId + ":" + brokenDate, scheduledFor);
    }

    public void celebrateMilestone(UUID userId, int milestone) {
        ProfileDto profile = profilesApi.find(userId).orElse(null);
        String body = composer.composeMilestone(milestone, profile);
        enqueue(userId, OutboxMessageType.STREAK_MILESTONE, body,
                "streak-milestone:" + userId + ":" + milestone, OffsetDateTime.now(clock));
    }

    public int sendDueReminders() {
        List<UUID> userIds = profilesApi.findUserIdsWithDailyReminder();
        int sent = 0;
        for (UUID userId : userIds) {
            if (sendReminderIfDue(userId)) {
                sent++;
            }
        }
        return sent;
    }

    boolean sendReminderIfDue(UUID userId) {
        ProfileDto profile = profilesApi.find(userId).orElse(null);
        if (profile == null || profile.getDailyReminder() == null) {
            return false;
        }
        ZonedDateTime localNow = OffsetDateTime.now(clock).atZoneSameInstant(zoneOf(profile));
        if (localNow.getHour() != profile.getDailyReminder().getHour()) {
            return false;
        }
        LocalDate today = localNow.toLocalDate();
        int streakCurrent = streaksApi.getStreak(userId).getStreakCurrent();
        String stepTitle = todaysStepTitle(userId, today);
        int dueReviews = (int) userMemoryApi.countDueReviews(userId, OffsetDateTime.now(clock));
        String body = composer.composeDailyReminder(streakCurrent, stepTitle, dueReviews, profile);
        enqueue(userId, OutboxMessageType.DAILY_REMINDER, body,
                "reminder:" + userId + ":" + today, OffsetDateTime.now(clock));
        return true;
    }

    private String todaysStepTitle(UUID userId, LocalDate today) {
        return planApi.getActivePlanForUser(userId)
                .map(LearningPlanDto::getSteps)
                .map(steps -> pickStep(steps, today))
                .map(PlanStepDto::getTitle)
                .orElse(null);
    }

    private static PlanStepDto pickStep(List<PlanStepDto> steps, LocalDate today) {
        return steps.stream()
                .filter(step -> !step.isDone() && today.equals(step.getScheduledDate()))
                .findFirst()
                .orElseGet(() -> steps.stream()
                        .filter(step -> !step.isDone())
                        .findFirst()
                        .orElse(null));
    }

    private void enqueue(UUID userId, OutboxMessageType type, String body, String dedupeKey,
                         OffsetDateTime scheduledFor) {
        notificationsApi.enqueue(OutboxMessageRequest.builder()
                .userId(userId)
                .channel(OutboxChannel.TELEGRAM)
                .type(type)
                .body(body)
                .dedupeKey(dedupeKey)
                .scheduledFor(scheduledFor)
                .build());
        log.debug("enqueued outbox message type={} user={} dedupeKey={}", type, userId, dedupeKey);
    }

    private static ZoneId zoneOf(ProfileDto profile) {
        if (profile == null || profile.getTimezone() == null || profile.getTimezone().isBlank()) {
            return DEFAULT_ZONE;
        }
        try {
            return ZoneId.of(profile.getTimezone());
        } catch (RuntimeException ex) {
            return DEFAULT_ZONE;
        }
    }
}
