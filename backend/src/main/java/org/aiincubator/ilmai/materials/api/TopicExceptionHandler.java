package org.aiincubator.ilmai.materials.api;

import lombok.RequiredArgsConstructor;
import org.aiincubator.ilmai.common.i18n.MessageService;
import org.aiincubator.ilmai.common.payload.ApiError;
import org.aiincubator.ilmai.common.payload.ApiResponse;
import org.aiincubator.ilmai.materials.service.TopicException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
@RequiredArgsConstructor
public class TopicExceptionHandler {

    private final MessageService messages;

    @ExceptionHandler(TopicException.class)
    public ResponseEntity<ApiResponse<Void>> handle(TopicException ex) {
        HttpStatus status = switch (ex.getReason()) {
            case TOPIC_NOT_FOUND, SPACE_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case TOPIC_NAME_BLANK -> HttpStatus.BAD_REQUEST;
            case TOPIC_NAME_TAKEN, TOPIC_NOT_EMPTY -> HttpStatus.CONFLICT;
        };
        String localized = messages.get(ex.getReason().getMessageKey(), ex.getMessageArgs());
        return ResponseEntity.status(status)
                .body(ApiResponse.fail(ApiError.of(ex.getReason().name(), localized)));
    }
}
