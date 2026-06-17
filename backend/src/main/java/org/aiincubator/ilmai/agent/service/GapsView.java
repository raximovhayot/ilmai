package org.aiincubator.ilmai.agent.service;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public final class GapsView {

    private final boolean ready;
    private final String narration;
    private final double overallAccuracy;
    private final int totalQuestionsAnswered;
    private final int correctCount;
    private final String recommendedNext;
    private final List<GapConceptView> gaps;
    private final List<GapConceptView> strengths;
}
