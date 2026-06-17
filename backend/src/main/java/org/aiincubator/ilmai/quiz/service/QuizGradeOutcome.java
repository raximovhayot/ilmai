package org.aiincubator.ilmai.quiz.service;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public final class QuizGradeOutcome {

    private final Boolean correct;
    private final String feedback;
    private final String correctAnswer;
    private final String explanation;
    private final String concept;
    private final int questionNumber;
    private final int answeredCount;
    private final int totalCount;
    private final int correctCount;
    private final boolean completed;
    private final int difficultyLevel;
}
