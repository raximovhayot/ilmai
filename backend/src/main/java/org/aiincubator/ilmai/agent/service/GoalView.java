package org.aiincubator.ilmai.agent.service;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public final class GoalView {

    private final boolean goalSet;
    private final String goal;
    private final String deadline;
    private final Long daysUntilDeadline;
}
