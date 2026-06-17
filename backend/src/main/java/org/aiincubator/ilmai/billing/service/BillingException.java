package org.aiincubator.ilmai.billing.service;

import lombok.Getter;

@Getter
public class BillingException extends RuntimeException {

    @Getter
    public enum Reason {
        INVALID_PLAN("billing.error.invalidPlan"),
        INVALID_PROVIDER("billing.error.invalidProvider"),
        INVALID_WEBHOOK("billing.error.invalidWebhook"),
        SUBSCRIPTION_NOT_FOUND("billing.error.subscriptionNotFound");

        private final String messageKey;

        Reason(String messageKey) {
            this.messageKey = messageKey;
        }
    }

    private final Reason reason;
    private final Object[] messageArgs;

    public BillingException(Reason reason) {
        this(reason, new Object[0]);
    }

    public BillingException(Reason reason, Object... messageArgs) {
        super(reason.messageKey);
        this.reason = reason;
        this.messageArgs = messageArgs == null ? new Object[0] : messageArgs;
    }
}
