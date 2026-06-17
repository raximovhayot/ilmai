package org.aiincubator.ilmai.plan;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Getter
@AllArgsConstructor
public final class PlanStepInput {

    private final int dayIndex;
    private final LocalDate scheduledDate;
    private final String title;
    private final PlanActivity activity;
    private final List<UUID> materialIds;
    private final String note;
}
