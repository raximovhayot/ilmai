package org.aiincubator.ilmai.streaks.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aiincubator.ilmai.streaks.domain.StreakActivityDayRepository;
import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.annotations.Recurring;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class StreakRolloverJob {

    static final String RECURRING_JOB_ID = "streak-rollover";

    private final StreakActivityDayRepository activityDays;
    private final StreakService streakService;

    @Recurring(id = RECURRING_JOB_ID, cron = "0 * * * *")
    @Job(name = "Streak daily rollover")
    public void run() {
        List<UUID> userIds = activityDays.findDistinctUserIds();
        for (UUID userId : userIds) {
            streakService.rolloverYesterday(userId);
        }
        log.debug("streak rollover processed {} active users", userIds.size());
    }
}
