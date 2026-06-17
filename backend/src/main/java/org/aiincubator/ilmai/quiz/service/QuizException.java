package org.aiincubator.ilmai.quiz.service;

import lombok.Getter;

@Getter
public class QuizException extends RuntimeException {

    @Getter
    public enum Reason {
        QUIZ_NOT_FOUND("quiz.error.notFound"),
        QUIZ_QUESTION_NOT_FOUND("quiz.error.questionNotFound"),
        QUIZ_ALREADY_ANSWERED("quiz.error.alreadyAnswered"),
        QUIZ_MATERIALS_MISSING("quiz.error.materialsMissing"),
        QUIZ_QUOTA_EXCEEDED("quiz.error.quotaExceeded");

        private final String messageKey;

        Reason(String messageKey) {
            this.messageKey = messageKey;
        }
    }

    private final Reason reason;
    private final Object[] messageArgs;

    public QuizException(Reason reason) {
        this(reason, new Object[0]);
    }

    public QuizException(Reason reason, Object... messageArgs) {
        super(reason.messageKey);
        this.reason = reason;
        this.messageArgs = messageArgs == null ? new Object[0] : messageArgs;
    }
}
