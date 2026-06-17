package org.aiincubator.ilmai.notifications;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder
public final class OutboxMessageRequest {

    private final UUID userId;
    private final OutboxChannel channel;
    private final OutboxMessageType type;
    private final String body;
    private final String dedupeKey;
    private final OffsetDateTime scheduledFor;
}
