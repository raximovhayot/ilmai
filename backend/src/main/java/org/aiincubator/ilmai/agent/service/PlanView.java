package org.aiincubator.ilmai.agent.service;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

@Getter
@AllArgsConstructor
public final class PlanView {

    private final boolean hasPlan;
    private final String goal;
    private final LocalDate targetDate;
    private final List<PlanStepView> steps;
    private final boolean replanNeeded;
}
