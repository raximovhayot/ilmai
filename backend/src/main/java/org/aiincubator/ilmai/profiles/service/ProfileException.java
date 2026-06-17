package org.aiincubator.ilmai.profiles.service;

import lombok.Getter;

@Getter
public class ProfileException extends RuntimeException {

    @Getter
    public enum Reason {
        PROFILE_NOT_FOUND("profile.error.notFound"),
        PROFILE_INVALID_LOCALE("profile.error.invalidLocale"),
        PROFILE_INVALID_TIMEZONE("profile.error.invalidTimezone"),
        PROFILE_INVALID_TARGET_DATE("profile.error.invalidTargetDate");

        private final String messageKey;

        Reason(String messageKey) {
            this.messageKey = messageKey;
        }
    }

    private final Reason reason;
    private final Object[] messageArgs;

    public ProfileException(Reason reason) {
        this(reason, new Object[0]);
    }

    public ProfileException(Reason reason, Object... messageArgs) {
        super(reason.messageKey);
        this.reason = reason;
        this.messageArgs = messageArgs == null ? new Object[0] : messageArgs;
    }
}
