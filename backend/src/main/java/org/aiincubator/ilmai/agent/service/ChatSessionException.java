package org.aiincubator.ilmai.agent.service;

import lombok.Getter;

@Getter
public class ChatSessionException extends RuntimeException {

    @Getter
    public enum Reason {
        SESSION_NOT_FOUND("agent.session.notFound");

        private final String messageKey;

        Reason(String messageKey) {
            this.messageKey = messageKey;
        }
    }

    private final Reason reason;
    private final Object[] messageArgs;

    public ChatSessionException(Reason reason) {
        this(reason, new Object[0]);
    }

    public ChatSessionException(Reason reason, Object... messageArgs) {
        super(reason.messageKey);
        this.reason = reason;
        this.messageArgs = messageArgs == null ? new Object[0] : messageArgs;
    }
}
