package org.aiincubator.ilmai.telegram.service;

import lombok.Getter;

@Getter
public class TelegramException extends RuntimeException {

    @Getter
    public enum Reason {
        TELEGRAM_INVALID_CODE("telegram.error.invalidCode"),
        TELEGRAM_ALREADY_LINKED("telegram.error.alreadyLinked"),
        TELEGRAM_NOT_LINKED("telegram.error.notLinked"),
        TELEGRAM_DISABLED("telegram.disabled"),
        TELEGRAM_WEBHOOK_FORBIDDEN("telegram.error.webhookForbidden");

        private final String messageKey;

        Reason(String messageKey) {
            this.messageKey = messageKey;
        }
    }

    private final Reason reason;
    private final Object[] messageArgs;

    public TelegramException(Reason reason) {
        this(reason, new Object[0]);
    }

    public TelegramException(Reason reason, Object... messageArgs) {
        super(reason.messageKey);
        this.reason = reason;
        this.messageArgs = messageArgs == null ? new Object[0] : messageArgs;
    }
}
