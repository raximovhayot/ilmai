package org.aiincubator.ilmai.plan;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@AllArgsConstructor
public final class LearningPlanDto {

    private final UUID id;
    private final String goal;
    private final LocalDate targetDate;
    private final PlanStatus status;
    private final OffsetDateTime createdAt;
    private final List<PlanStepDto> steps;
    private final boolean replanNeeded;
}
