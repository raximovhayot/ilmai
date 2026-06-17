package org.aiincubator.ilmai.quiz.api;

import lombok.RequiredArgsConstructor;
import org.aiincubator.ilmai.common.i18n.MessageService;
import org.aiincubator.ilmai.common.payload.ApiError;
import org.aiincubator.ilmai.common.payload.ApiResponse;
import org.aiincubator.ilmai.quiz.service.QuizException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
@RequiredArgsConstructor
public class QuizExceptionHandler {

    private final MessageService messages;

    @ExceptionHandler(QuizException.class)
    public ResponseEntity<ApiResponse<Void>> handle(QuizException ex) {
        HttpStatus status = switch (ex.getReason()) {
            case QUIZ_NOT_FOUND, QUIZ_QUESTION_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case QUIZ_ALREADY_ANSWERED, QUIZ_MATERIALS_MISSING -> HttpStatus.BAD_REQUEST;
            case QUIZ_QUOTA_EXCEEDED -> HttpStatus.TOO_MANY_REQUESTS;
        };
        String localized = messages.get(ex.getReason().getMessageKey(), ex.getMessageArgs());
        return ResponseEntity.status(status)
                .body(ApiResponse.fail(ApiError.of(ex.getReason().name(), localized)));
    }
}
