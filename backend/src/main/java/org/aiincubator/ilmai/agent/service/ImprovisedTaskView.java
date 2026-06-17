package org.aiincubator.ilmai.agent.service;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public final class ImprovisedTaskView {

    private final boolean hasSuggestion;
    private final String kind;
    private final String concept;
    private final String materialTitle;
    private final int questionCount;
    private final String label;
}
