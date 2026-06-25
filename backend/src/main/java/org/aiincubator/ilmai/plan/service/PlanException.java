package org.aiincubator.ilmai.plan.service;

import lombok.Getter;

@Getter
public class PlanException extends RuntimeException {

    @Getter
    public enum Reason {
        PLAN_NOT_FOUND("plan.error.notFound"),
        PLAN_STEP_NOT_FOUND("plan.error.stepNotFound"),
        PLAN_STATUS_INVALID("plan.error.statusInvalid"),
        PLAN_LESSON_UNAVAILABLE("plan.error.lessonUnavailable"),
        PLAN_LESSON_QUOTA_EXCEEDED("plan.error.lessonQuotaExceeded"),
        PLAN_STEP_NOT_COMPLETABLE("plan.error.stepNotCompletable");

        private final String messageKey;

        Reason(String messageKey) {
            this.messageKey = messageKey;
        }
    }

    private final Reason reason;
    private final Object[] messageArgs;

    public PlanException(Reason reason) {
        this(reason, new Object[0]);
    }

    public PlanException(Reason reason, Object... messageArgs) {
        super(reason.messageKey);
        this.reason = reason;
        this.messageArgs = messageArgs == null ? new Object[0] : messageArgs;
    }
}
