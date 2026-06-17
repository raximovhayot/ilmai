package org.aiincubator.ilmai.notifications.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.annotations.Recurring;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DailyReminderJob {

    static final String RECURRING_JOB_ID = "daily-reminder";

    private final NotificationService notificationService;

    @Recurring(id = RECURRING_JOB_ID, cron = "0 * * * *")
    @Job(name = "Daily study reminder")
    public void run() {
        int enqueued = notificationService.sendDueReminders();
        log.debug("daily reminder job enqueued {} reminder(s)", enqueued);
    }
}
