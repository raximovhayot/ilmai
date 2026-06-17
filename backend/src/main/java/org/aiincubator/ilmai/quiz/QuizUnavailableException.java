package org.aiincubator.ilmai.quiz;

import lombok.Getter;

@Getter
public class QuizUnavailableException extends RuntimeException {

    private final QuizUnavailableReason reason;

    public QuizUnavailableException(QuizUnavailableReason reason) {
        super("quiz unavailable: " + reason);
        this.reason = reason;
    }
}
