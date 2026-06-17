package org.aiincubator.ilmai.notifications;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface NotificationsApi {

    OutboxMessageDto enqueue(OutboxMessageRequest request);

    List<OutboxMessageDto> findPending(OffsetDateTime asOf);

    void markSent(UUID id);
}
