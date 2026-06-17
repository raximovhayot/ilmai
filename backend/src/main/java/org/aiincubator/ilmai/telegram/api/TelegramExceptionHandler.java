package org.aiincubator.ilmai.telegram.api;

import lombok.RequiredArgsConstructor;
import org.aiincubator.ilmai.common.i18n.MessageService;
import org.aiincubator.ilmai.common.payload.ApiError;
import org.aiincubator.ilmai.common.payload.ApiResponse;
import org.aiincubator.ilmai.telegram.service.TelegramException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
@RequiredArgsConstructor
public class TelegramExceptionHandler {

    private final MessageService messages;

    @ExceptionHandler(TelegramException.class)
    public ResponseEntity<ApiResponse<Void>> handle(TelegramException ex) {
        HttpStatus status = switch (ex.getReason()) {
            case TELEGRAM_INVALID_CODE, TELEGRAM_ALREADY_LINKED -> HttpStatus.BAD_REQUEST;
            case TELEGRAM_NOT_LINKED -> HttpStatus.NOT_FOUND;
            case TELEGRAM_DISABLED -> HttpStatus.SERVICE_UNAVAILABLE;
            case TELEGRAM_WEBHOOK_FORBIDDEN -> HttpStatus.FORBIDDEN;
        };
        String localized = messages.get(ex.getReason().getMessageKey(), ex.getMessageArgs());
        return ResponseEntity.status(status)
                .body(ApiResponse.fail(ApiError.of(ex.getReason().name(), localized)));
    }
}
