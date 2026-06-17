package org.aiincubator.ilmai.agent.service;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public final class GapConceptView {

    private final String concept;
    private final double accuracy;
    private final int hitCount;
    private final int missCount;
    private final String suggestedMaterial;
    private final String trend;
}
