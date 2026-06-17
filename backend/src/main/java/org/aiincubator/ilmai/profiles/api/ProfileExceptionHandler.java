package org.aiincubator.ilmai.profiles.api;

import lombok.RequiredArgsConstructor;
import org.aiincubator.ilmai.common.i18n.MessageService;
import org.aiincubator.ilmai.common.payload.ApiError;
import org.aiincubator.ilmai.common.payload.ApiResponse;
import org.aiincubator.ilmai.profiles.service.ProfileException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
@RequiredArgsConstructor
public class ProfileExceptionHandler {

    private final MessageService messages;

    @ExceptionHandler(ProfileException.class)
    public ResponseEntity<ApiResponse<Void>> handle(ProfileException ex) {
        HttpStatus status = switch (ex.getReason()) {
            case PROFILE_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case PROFILE_INVALID_LOCALE, PROFILE_INVALID_TIMEZONE, PROFILE_INVALID_TARGET_DATE -> HttpStatus.BAD_REQUEST;
        };
        String localized = messages.get(ex.getReason().getMessageKey(), ex.getMessageArgs());
        return ResponseEntity.status(status)
                .body(ApiResponse.fail(ApiError.of(ex.getReason().name(), localized)));
    }
}
