package org.aiincubator.ilmai.agent;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@AllArgsConstructor
public final class ReviewDueDto {

    private final String concept;
    private final OffsetDateTime nextReviewAt;
    private final UUID materialId;
    private final int timesWrong;
}
