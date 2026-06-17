package org.aiincubator.ilmai.spaces.api;

import lombok.RequiredArgsConstructor;
import org.aiincubator.ilmai.common.i18n.MessageService;
import org.aiincubator.ilmai.common.payload.ApiError;
import org.aiincubator.ilmai.common.payload.ApiResponse;
import org.aiincubator.ilmai.spaces.service.SpaceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
@RequiredArgsConstructor
public class SpaceExceptionHandler {

    private final MessageService messages;

    @ExceptionHandler(SpaceException.class)
    public ResponseEntity<ApiResponse<Void>> handle(SpaceException ex) {
        HttpStatus status = switch (ex.getReason()) {
            case SPACE_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case NAME_BLANK -> HttpStatus.BAD_REQUEST;
        };
        String localized = messages.get(ex.getReason().getMessageKey(), ex.getMessageArgs());
        return ResponseEntity.status(status)
                .body(ApiResponse.fail(ApiError.of(ex.getReason().name(), localized)));
    }
}
