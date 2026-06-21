package org.aiincubator.ilmai.plan.payload;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.aiincubator.ilmai.plan.PlanStatus;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LearningPlanResponse {

    private UUID id;
    private UUID goalId;
    private String goal;
    private LocalDate targetDate;
    private PlanStatus status;
    private boolean replanNeeded;
    private OffsetDateTime createdAt;
    private int daysTotal;
    private int daysCompleted;
    private List<PlanStepResponse> steps;
}
