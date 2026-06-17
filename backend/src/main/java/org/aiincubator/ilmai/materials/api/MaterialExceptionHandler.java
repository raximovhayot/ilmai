package org.aiincubator.ilmai.materials.api;

import lombok.RequiredArgsConstructor;
import org.aiincubator.ilmai.common.i18n.MessageService;
import org.aiincubator.ilmai.common.payload.ApiError;
import org.aiincubator.ilmai.common.payload.ApiResponse;
import org.aiincubator.ilmai.materials.service.MaterialException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
@RequiredArgsConstructor
public class MaterialExceptionHandler {

    private final MessageService messages;

    @ExceptionHandler(MaterialException.class)
    public ResponseEntity<ApiResponse<Void>> handle(MaterialException ex) {
        HttpStatus status = switch (ex.getReason()) {
            case MATERIAL_NOT_FOUND, MATERIAL_TOPIC_NOT_FOUND, MATERIAL_SPACE_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case MATERIAL_TITLE_BLANK, MATERIAL_UNSUPPORTED_TYPE -> HttpStatus.BAD_REQUEST;
            case MATERIAL_TOO_LARGE -> HttpStatus.CONTENT_TOO_LARGE;
            case MATERIAL_STORAGE_FAILED -> HttpStatus.SERVICE_UNAVAILABLE;
            case MATERIAL_UPLOAD_LIMIT -> HttpStatus.PAYMENT_REQUIRED;
        };
        String localized = messages.get(ex.getReason().getMessageKey(), ex.getMessageArgs());
        return ResponseEntity.status(status)
                .body(ApiResponse.fail(ApiError.of(ex.getReason().name(), localized)));
    }
}
