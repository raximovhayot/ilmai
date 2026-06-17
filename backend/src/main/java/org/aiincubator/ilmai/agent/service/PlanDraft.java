package org.aiincubator.ilmai.agent.service;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.aiincubator.ilmai.plan.PlanStepInput;

import java.util.List;

@Getter
@AllArgsConstructor
public final class PlanDraft {

    private final List<PlanStepInput> steps;
    private final int ilmTokenCost;
}
