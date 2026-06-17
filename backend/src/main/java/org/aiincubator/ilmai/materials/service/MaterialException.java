package org.aiincubator.ilmai.materials.service;

import lombok.Getter;

@Getter
public class MaterialException extends RuntimeException {

    @Getter
    public enum Reason {
        MATERIAL_NOT_FOUND("material.error.notFound"),
        MATERIAL_TOPIC_NOT_FOUND("material.error.topicNotFound"),
        MATERIAL_SPACE_NOT_FOUND("material.error.spaceNotFound"),
        MATERIAL_TITLE_BLANK("material.error.titleBlank"),
        MATERIAL_UNSUPPORTED_TYPE("material.error.unsupportedType"),
        MATERIAL_TOO_LARGE("material.error.tooLarge"),
        MATERIAL_STORAGE_FAILED("material.error.storageFailed"),
        MATERIAL_UPLOAD_LIMIT("material.error.uploadLimitReached");

        private final String messageKey;

        Reason(String messageKey) {
            this.messageKey = messageKey;
        }
    }

    private final Reason reason;
    private final Object[] messageArgs;

    public MaterialException(Reason reason) {
        this(reason, new Object[0]);
    }

    public MaterialException(Reason reason, Object... messageArgs) {
        super(reason.messageKey);
        this.reason = reason;
        this.messageArgs = messageArgs == null ? new Object[0] : messageArgs;
    }
}
