package org.aiincubator.ilmai.quiz;

import lombok.Getter;

@Getter
public class QuizGradeException extends RuntimeException {

    private final QuizGradeReason reason;

    public QuizGradeException(QuizGradeReason reason) {
        super("quiz answer could not be graded: " + reason);
        this.reason = reason;
    }
}
