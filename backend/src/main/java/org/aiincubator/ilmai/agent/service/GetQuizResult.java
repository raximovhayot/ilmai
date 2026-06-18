package org.aiincubator.ilmai.agent.service;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

@Getter
@AllArgsConstructor
public final class GetQuizResult {

    private final boolean found;
    private final UUID sessionId;
    private final int questionCount;
    private final int answeredCount;
    private final int correctCount;
    private final List<QuizQuestionView> questions;
}
