package org.aiincubator.ilmai.quiz.service;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

@Getter
@AllArgsConstructor
@ToString
public class QuizGradeResult {

    private final Boolean correct;
    private final String feedback;
}
