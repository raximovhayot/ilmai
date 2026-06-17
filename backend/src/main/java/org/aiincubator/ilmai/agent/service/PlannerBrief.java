package org.aiincubator.ilmai.agent.service;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

@Getter
@AllArgsConstructor
public final class PlannerBrief {

    private final String language;
    private final String goal;
    private final LocalDate targetDate;
    private final Integer daysUntilDeadline;
    private final int planDays;
    private final Integer dailyStudyMinutes;
    private final List<String> weakConcepts;
}
