package org.aiincubator.ilmai.agent;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@AllArgsConstructor
public final class ChatSessionSummary {

    private final UUID id;
    private final String title;
    private final OffsetDateTime createdAt;
    private final boolean active;
}
