package org.aiincubator.ilmai.quiz;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public final class WeeklyQuizStats {

    private final int quizzes;
    private final int answered;
    private final int correct;
}
