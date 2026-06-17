package org.aiincubator.ilmai.gaps.service;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@ToString
class ConceptAggregate {

    private int hitCount;
    private int missCount;
    private OffsetDateTime lastSeenAt;
    private UUID suggestedMaterialId;
    private final List<GapAnswerSample> samples = new ArrayList<>();
}
