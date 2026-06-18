package org.aiincubator.ilmai.agent.service;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public final class GetQuizzesResult {

    private final boolean hasQuizzes;
    private final List<QuizSummaryView> quizzes;
}
