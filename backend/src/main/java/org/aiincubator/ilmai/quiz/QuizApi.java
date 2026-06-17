package org.aiincubator.ilmai.quiz;

import org.aiincubator.ilmai.common.CurrentUser;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface QuizApi {

    List<QuizQuestionDto> findIncorrectQuestionsForUser(UUID userId);

    List<QuizSessionDto> findAllSessionsForUser(UUID userId);

    QuizCardDto startQuiz(CurrentUser currentUser, String scope, Integer questionCount, String difficulty);

    QuizGradeDto gradeAnswer(CurrentUser currentUser, UUID sessionId, int questionNumber, String answer);

    QuizPollSpecDto resolveQuizPoll(CurrentUser currentUser, UUID sessionId, int questionNumber);

    WeeklyQuizStats weeklyStats(UUID userId, OffsetDateTime since);
}
