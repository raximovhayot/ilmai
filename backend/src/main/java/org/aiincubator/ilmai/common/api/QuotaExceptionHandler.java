package org.aiincubator.ilmai.common.api;

import lombok.RequiredArgsConstructor;
import org.aiincubator.ilmai.common.i18n.MessageService;
import org.aiincubator.ilmai.common.payload.ApiError;
import org.aiincubator.ilmai.common.payload.ApiResponse;
import org.aiincubator.ilmai.common.quota.FeatureLockedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
@RequiredArgsConstructor
public class QuotaExceptionHandler {

    private final MessageService messages;

    @ExceptionHandler(FeatureLockedException.class)
    public ResponseEntity<ApiResponse<Void>> handle(FeatureLockedException ex) {
        String localized = messages.get("billing.error.featureLocked");
        return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED)
                .body(ApiResponse.fail(ApiError.of(ex.getFeature().name(), localized)));
    }
}
