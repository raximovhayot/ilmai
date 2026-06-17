package org.aiincubator.ilmai.agent.service;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

@Getter
@AllArgsConstructor
public final class PlanStepView {

    private final int day;
    private final LocalDate date;
    private final String title;
    private final String activity;
    private final List<String> materials;
    private final String note;
    private final boolean done;
}
