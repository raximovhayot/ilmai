package org.aiincubator.ilmai.plan.payload;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class CompleteStepRequest {

    private String reflectionNote;
    private UUID quizSessionId;
}
