package org.aiincubator.ilmai.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class UserActivityRecordedEvent {

    private final UUID userId;
    private final OffsetDateTime occurredAt;
}
