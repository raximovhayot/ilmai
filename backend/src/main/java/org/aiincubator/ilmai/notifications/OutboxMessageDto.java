package org.aiincubator.ilmai.notifications;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@AllArgsConstructor
public final class OutboxMessageDto {

    private final UUID id;
    private final UUID userId;
    private final OutboxChannel channel;
    private final OutboxMessageType type;
    private final String body;
    private final OffsetDateTime scheduledFor;
    private final OffsetDateTime sentAt;
    private final String dedupeKey;
    private final OffsetDateTime createdAt;
}
