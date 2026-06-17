package org.aiincubator.ilmai.telegram.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.annotations.Recurring;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxDrainJob {

    static final String RECURRING_JOB_ID = "telegram-outbox-drain";

    private final OutboxDrainService outboxDrainService;

    @Recurring(id = RECURRING_JOB_ID, cron = "* * * * *")
    @Job(name = "Telegram outbox drain")
    public void run() {
        int delivered = outboxDrainService.drain();
        log.debug("telegram outbox drain delivered {} message(s)", delivered);
    }
}
