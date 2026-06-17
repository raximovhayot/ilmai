package org.aiincubator.ilmai.streaks.service;

import lombok.RequiredArgsConstructor;
import org.aiincubator.ilmai.common.UserActivityRecordedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class StreakActivityListener {

    private final StreakService streakService;

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onUserActivityRecorded(UserActivityRecordedEvent event) {
        streakService.recordActivity(event.getUserId(), event.getOccurredAt());
    }
}
