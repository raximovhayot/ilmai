package org.aiincubator.ilmai.rooms.api;

import lombok.RequiredArgsConstructor;
import org.aiincubator.ilmai.common.i18n.MessageService;
import org.aiincubator.ilmai.common.payload.ApiError;
import org.aiincubator.ilmai.common.payload.ApiResponse;
import org.aiincubator.ilmai.rooms.service.RoomException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
@RequiredArgsConstructor
public class RoomExceptionHandler {

    private final MessageService messages;

    @ExceptionHandler(RoomException.class)
    public ResponseEntity<ApiResponse<Void>> handle(RoomException ex) {
        HttpStatus status = switch (ex.getReason()) {
            case ROOM_NOT_FOUND, INVALID_INVITE, MEMBER_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case NAME_BLANK, INVALID_TARGET_DATE, OWNER_CANNOT_LEAVE, CANNOT_REMOVE_OWNER -> HttpStatus.BAD_REQUEST;
            case NOT_A_MEMBER, NOT_OWNER, PREMIUM_REQUIRED -> HttpStatus.FORBIDDEN;
        };
        String localized = messages.get(ex.getReason().getMessageKey(), ex.getMessageArgs());
        return ResponseEntity.status(status)
                .body(ApiResponse.fail(ApiError.of(ex.getReason().name(), localized)));
    }
}
