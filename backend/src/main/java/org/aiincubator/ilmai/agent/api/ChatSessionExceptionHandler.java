package org.aiincubator.ilmai.agent.api;

import lombok.RequiredArgsConstructor;
import org.aiincubator.ilmai.agent.service.ChatSessionException;
import org.aiincubator.ilmai.common.i18n.MessageService;
import org.aiincubator.ilmai.common.payload.ApiError;
import org.aiincubator.ilmai.common.payload.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
@RequiredArgsConstructor
public class ChatSessionExceptionHandler {

    private final MessageService messages;

    @ExceptionHandler(ChatSessionException.class)
    public ResponseEntity<ApiResponse<Void>> handle(ChatSessionException ex) {
        HttpStatus status = switch (ex.getReason()) {
            case SESSION_NOT_FOUND -> HttpStatus.NOT_FOUND;
        };
        String localized = messages.get(ex.getReason().getMessageKey(), ex.getMessageArgs());
        return ResponseEntity.status(status)
                .body(ApiResponse.fail(ApiError.of(ex.getReason().name(), localized)));
    }
}
