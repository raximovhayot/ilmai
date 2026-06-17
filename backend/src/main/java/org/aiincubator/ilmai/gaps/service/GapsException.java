package org.aiincubator.ilmai.gaps.service;

import lombok.Getter;

@Getter
public class GapsException extends RuntimeException {

    @Getter
    public enum Reason {
        GAPS_NOT_READY("gaps.error.notReady");

        private final String messageKey;

        Reason(String messageKey) {
            this.messageKey = messageKey;
        }
    }

    private final Reason reason;
    private final Object[] messageArgs;

    public GapsException(Reason reason) {
        this(reason, new Object[0]);
    }

    public GapsException(Reason reason, Object... messageArgs) {
        super(reason.messageKey);
        this.reason = reason;
        this.messageArgs = messageArgs == null ? new Object[0] : messageArgs;
    }
}
