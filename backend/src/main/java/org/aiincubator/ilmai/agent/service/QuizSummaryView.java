package org.aiincubator.ilmai.agent.service;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor
public final class QuizSummaryView {

    private final UUID sessionId;
    private final int questionCount;
    private final int answeredCount;
    private final int correctCount;
}
