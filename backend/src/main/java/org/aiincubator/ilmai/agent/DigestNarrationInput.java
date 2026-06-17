package org.aiincubator.ilmai.agent;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class DigestNarrationInput {

    private final String language;
    private final String goal;
    private final Integer daysUntilDeadline;
    private final int activeDays;
    private final int quizzes;
    private final int answered;
    private final int correct;
    private final Integer avgScorePercent;
    private final int planDone;
    private final int planTotal;
    private final int streakNow;
    private final List<String> topGaps;
}
