package org.aiincubator.ilmai.agent.service;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.aiincubator.ilmai.quiz.QuizGradeDto;

@Getter
@AllArgsConstructor
public final class GradeAnswerResult {

    private final boolean graded;
    private final QuizGradeDto result;
    private final String reason;
}
