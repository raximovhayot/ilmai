package org.aiincubator.ilmai.notifications.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aiincubator.ilmai.streaks.StreakBrokenEvent;
import org.aiincubator.ilmai.streaks.StreakMilestoneReachedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class StreakNotificationListener {

    private final NotificationService notificationService;

    @EventListener
    public void onStreakBroken(StreakBrokenEvent event) {
        try {
            notificationService.nudgeBrokenStreak(
                    event.getUserId(), event.getBrokenDate(), event.getBrokenStreakLength());
        } catch (RuntimeException ex) {
            log.warn("failed to enqueue broken-streak nudge for user {}", event.getUserId(), ex);
        }
    }

    @EventListener
    public void onStreakMilestone(StreakMilestoneReachedEvent event) {
        try {
            notificationService.celebrateMilestone(event.getUserId(), event.getMilestone());
        } catch (RuntimeException ex) {
            log.warn("failed to enqueue streak milestone for user {}", event.getUserId(), ex);
        }
    }
}
