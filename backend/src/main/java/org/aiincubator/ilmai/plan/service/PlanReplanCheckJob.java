package org.aiincubator.ilmai.plan.service;

import lombok.extern.slf4j.Slf4j;
import org.aiincubator.ilmai.plan.PlanStatus;
import org.aiincubator.ilmai.plan.domain.LearningPlanRepository;
import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.annotations.Recurring;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Slf4j
@Component
public class PlanReplanCheckJob {

    static final String RECURRING_JOB_ID = "plan-replan-check";

    private final LearningPlanRepository learningPlanRepository;
    private final PlanService planService;
    private final int behindThresholdDays;

    public PlanReplanCheckJob(LearningPlanRepository learningPlanRepository,
                              PlanService planService,
                              @Value("${plan.replan.behind-threshold-days:3}") int behindThresholdDays) {
        this.learningPlanRepository = learningPlanRepository;
        this.planService = planService;
        this.behindThresholdDays = behindThresholdDays;
    }

    @Recurring(id = RECURRING_JOB_ID, cron = "0 * * * *")
    @Job(name = "Plan replan check (behind-by-N)")
    public void run() {
        List<UUID> userIds = learningPlanRepository.findDistinctUserIdsByStatus(PlanStatus.ACTIVE);
        int flagged = 0;
        for (UUID userId : userIds) {
            if (planService.flagIfBehind(userId, behindThresholdDays)) {
                flagged++;
            }
        }
        log.debug("plan replan check: {} active-plan users, {} flagged behind by >= {} day(s)",
                userIds.size(), flagged, behindThresholdDays);
    }
}
