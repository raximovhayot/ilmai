package org.aiincubator.ilmai.materials.service;

import lombok.Getter;

@Getter
public class TopicException extends RuntimeException {

    @Getter
    public enum Reason {
        TOPIC_NOT_FOUND("topic.error.notFound"),
        TOPIC_NAME_BLANK("topic.error.nameBlank"),
        TOPIC_NAME_TAKEN("topic.error.nameTaken"),
        TOPIC_NOT_EMPTY("topic.error.notEmpty"),
        SPACE_NOT_FOUND("topic.error.spaceNotFound");

        private final String messageKey;

        Reason(String messageKey) {
            this.messageKey = messageKey;
        }
    }

    private final Reason reason;
    private final Object[] messageArgs;

    public TopicException(Reason reason) {
        this(reason, new Object[0]);
    }

    public TopicException(Reason reason, Object... messageArgs) {
        super(reason.messageKey);
        this.reason = reason;
        this.messageArgs = messageArgs == null ? new Object[0] : messageArgs;
    }
}
