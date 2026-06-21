package org.aiincubator.ilmai.plan.payload;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.aiincubator.ilmai.plan.PlanStatus;

@Getter
@Setter
@NoArgsConstructor
public class UpdatePlanStatusRequest {

    @NotNull
    private PlanStatus status;
}
