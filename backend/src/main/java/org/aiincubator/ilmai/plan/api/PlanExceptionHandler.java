package org.aiincubator.ilmai.plan.api;

import lombok.RequiredArgsConstructor;
import org.aiincubator.ilmai.common.i18n.MessageService;
import org.aiincubator.ilmai.common.payload.ApiError;
import org.aiincubator.ilmai.common.payload.ApiResponse;
import org.aiincubator.ilmai.plan.service.PlanException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
@RequiredArgsConstructor
public class PlanExceptionHandler {

    private final MessageService messages;

    @ExceptionHandler(PlanException.class)
    public ResponseEntity<ApiResponse<Void>> handle(PlanException ex) {
        HttpStatus status = switch (ex.getReason()) {
            case PLAN_NOT_FOUND, PLAN_STEP_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case PLAN_STATUS_INVALID -> HttpStatus.BAD_REQUEST;
            case PLAN_LESSON_UNAVAILABLE -> HttpStatus.UNPROCESSABLE_ENTITY;
            case PLAN_LESSON_QUOTA_EXCEEDED -> HttpStatus.TOO_MANY_REQUESTS;
        };
        String localized = messages.get(ex.getReason().getMessageKey(), ex.getMessageArgs());
        return ResponseEntity.status(status)
                .body(ApiResponse.fail(ApiError.of(ex.getReason().name(), localized)));
    }
}
