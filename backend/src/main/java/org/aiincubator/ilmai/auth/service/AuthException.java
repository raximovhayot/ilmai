package org.aiincubator.ilmai.auth.service;

import lombok.Getter;

@Getter
public class AuthException extends RuntimeException {

    @Getter
    public enum Reason {
        INVALID_GOOGLE_TOKEN("auth.error.invalidGoogleToken"),
        EMAIL_NOT_VERIFIED("auth.error.emailNotVerified"),
        INVALID_REFRESH_TOKEN("auth.error.invalidRefreshToken"),
        REFRESH_TOKEN_REUSED("auth.error.refreshTokenReused"),
        USER_DISABLED("auth.error.userDisabled"),
        USER_NOT_FOUND("auth.error.userNotFound"),
        DEV_LOGIN_DISABLED("auth.error.devLoginDisabled");

        private final String messageKey;

        Reason(String messageKey) {
            this.messageKey = messageKey;
        }
    }

    private final Reason reason;
    private final Object[] messageArgs;

    public AuthException(Reason reason) {
        this(reason, new Object[0]);
    }

    public AuthException(Reason reason, Object... messageArgs) {
        super(reason.messageKey);
        this.reason = reason;
        this.messageArgs = messageArgs == null ? new Object[0] : messageArgs;
    }
}
