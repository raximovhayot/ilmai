package org.aiincubator.ilmai.spaces.service;

import lombok.Getter;

@Getter
public class SpaceException extends RuntimeException {

    @Getter
    public enum Reason {
        SPACE_NOT_FOUND("space.error.notFound"),
        NAME_BLANK("space.error.nameBlank");

        private final String messageKey;

        Reason(String messageKey) {
            this.messageKey = messageKey;
        }
    }

    private final Reason reason;
    private final Object[] messageArgs;

    public SpaceException(Reason reason) {
        this(reason, new Object[0]);
    }

    public SpaceException(Reason reason, Object... messageArgs) {
        super(reason.messageKey);
        this.reason = reason;
        this.messageArgs = messageArgs == null ? new Object[0] : messageArgs;
    }
}
