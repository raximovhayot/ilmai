package org.aiincubator.ilmai.agent;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@AllArgsConstructor
public final class UserFactDto {

    private final UUID id;
    private final String content;
    private final OffsetDateTime createdAt;
}
