package org.aiincubator.ilmai.gaps.service;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.OffsetDateTime;

@Getter
@AllArgsConstructor
final class GapAnswerSample {

    private final OffsetDateTime answeredAt;
    private final boolean correct;
}
