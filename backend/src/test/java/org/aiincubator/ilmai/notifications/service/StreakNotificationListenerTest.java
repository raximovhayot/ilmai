package org.aiincubator.ilmai.notifications.service;

import org.aiincubator.ilmai.streaks.StreakBrokenEvent;
import org.aiincubator.ilmai.streaks.StreakMilestoneReachedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class StreakNotificationListenerTest {

    @Mock NotificationService notificationService;
    @InjectMocks StreakNotificationListener listener;

    private final UUID userId = UUID.randomUUID();

    @Test
    void onStreakBroken_forwardsToService() {
        listener.onStreakBroken(new StreakBrokenEvent(userId, LocalDate.parse("2026-06-01"), 4));

        verify(notificationService).nudgeBrokenStreak(userId, LocalDate.parse("2026-06-01"), 4);
    }

    @Test
    void onStreakMilestone_forwardsToService() {
        listener.onStreakMilestone(new StreakMilestoneReachedEvent(userId, 7));

        verify(notificationService).celebrateMilestone(userId, 7);
    }

    @Test
    void onStreakBroken_swallowsServiceFailure() {
        doThrow(new RuntimeException("boom")).when(notificationService)
                .nudgeBrokenStreak(userId, LocalDate.parse("2026-06-01"), 4);

        assertThatCode(() -> listener.onStreakBroken(
                new StreakBrokenEvent(userId, LocalDate.parse("2026-06-01"), 4)))
                .doesNotThrowAnyException();
    }

    @Test
    void onStreakMilestone_swallowsServiceFailure() {
        doThrow(new RuntimeException("boom")).when(notificationService).celebrateMilestone(userId, 7);

        assertThatCode(() -> listener.onStreakMilestone(new StreakMilestoneReachedEvent(userId, 7)))
                .doesNotThrowAnyException();
    }
}
