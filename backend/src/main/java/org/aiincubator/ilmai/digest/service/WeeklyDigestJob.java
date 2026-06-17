package org.aiincubator.ilmai.digest.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.annotations.Recurring;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class WeeklyDigestJob {

    static final String RECURRING_JOB_ID = "weekly-digest";

    private final DigestService digestService;

    @Recurring(id = RECURRING_JOB_ID, cron = "0 * * * *")
    @Job(name = "Weekly digest")
    public void run() {
        int generated = digestService.generateDueDigests();
        log.debug("weekly digest job generated {} digest(s)", generated);
    }
}
