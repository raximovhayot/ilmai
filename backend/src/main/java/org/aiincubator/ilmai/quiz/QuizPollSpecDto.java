package org.aiincubator.ilmai.quiz;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class QuizPollSpecDto {

    private final int correctOptionId;
    private final String explanation;
}
