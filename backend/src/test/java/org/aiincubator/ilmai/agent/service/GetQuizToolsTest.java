package org.aiincubator.ilmai.agent.service;

import org.aiincubator.ilmai.common.CurrentUser;
import org.aiincubator.ilmai.quiz.QuizApi;
import org.aiincubator.ilmai.quiz.QuizCardDto;
import org.aiincubator.ilmai.quiz.QuizGradeDto;
import org.aiincubator.ilmai.quiz.QuizPollSpecDto;
import org.aiincubator.ilmai.quiz.QuizQuestionDto;
import org.aiincubator.ilmai.quiz.QuizSessionDto;
import org.aiincubator.ilmai.quiz.WeeklyQuizStats;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GetQuizToolsTest {

    private final UUID userA = UUID.randomUUID();

    @Test
    void getQuizzesSummarisesUserSessions() {
        UUID sessionId = UUID.randomUUID();
        QuizSessionDto session = new QuizSessionDto(sessionId, userA, List.of(
                question(true), question(false), question(null)));
        FakeQuizApi quizApi = new FakeQuizApi(new AtomicReference<>(), List.of(session), session);
        GetQuizzesTool tool = new GetQuizzesTool(quizApi);

        GetQuizzesResult result = tool.getQuizzes(toolContext(userA));

        assertThat(result.isHasQuizzes()).isTrue();
        assertThat(result.getQuizzes()).hasSize(1);
        QuizSummaryView summary = result.getQuizzes().get(0);
        assertThat(summary.getSessionId()).isEqualTo(sessionId);
        assertThat(summary.getQuestionCount()).isEqualTo(3);
        assertThat(summary.getAnsweredCount()).isEqualTo(2);
        assertThat(summary.getCorrectCount()).isEqualTo(1);
    }

    @Test
    void getQuizzesReportsNoQuizzesWhenEmpty() {
        FakeQuizApi quizApi = new FakeQuizApi(new AtomicReference<>(), List.of(), null);
        GetQuizzesTool tool = new GetQuizzesTool(quizApi);

        GetQuizzesResult result = tool.getQuizzes(toolContext(userA));

        assertThat(result.isHasQuizzes()).isFalse();
        assertThat(result.getQuizzes()).isEmpty();
    }

    @Test
    void getQuizReturnsSessionAndForwardsCurrentUser() {
        UUID sessionId = UUID.randomUUID();
        QuizSessionDto session = new QuizSessionDto(sessionId, userA, List.of(question(true), question(null)));
        AtomicReference<CurrentUser> captured = new AtomicReference<>();
        FakeQuizApi quizApi = new FakeQuizApi(captured, List.of(session), session);
        GetQuizTool tool = new GetQuizTool(quizApi);

        GetQuizResult result = tool.getQuiz(sessionId.toString(), toolContext(userA));

        assertThat(captured.get()).isNotNull();
        assertThat(captured.get().getUserId()).isEqualTo(userA);
        assertThat(result.isFound()).isTrue();
        assertThat(result.getSessionId()).isEqualTo(sessionId);
        assertThat(result.getQuestionCount()).isEqualTo(2);
        assertThat(result.getAnsweredCount()).isEqualTo(1);
        assertThat(result.getCorrectCount()).isEqualTo(1);
        assertThat(result.getQuestions()).hasSize(2);
        assertThat(result.getQuestions().get(0).getPosition()).isEqualTo(1);
    }

    @Test
    void getQuizReturnsNotFoundWhenSessionMissing() {
        FakeQuizApi quizApi = new FakeQuizApi(new AtomicReference<>(), List.of(), null);
        GetQuizTool tool = new GetQuizTool(quizApi);

        GetQuizResult result = tool.getQuiz(UUID.randomUUID().toString(), toolContext(userA));

        assertThat(result.isFound()).isFalse();
        assertThat(result.getSessionId()).isNull();
        assertThat(result.getQuestions()).isEmpty();
    }

    @Test
    void getQuizReturnsNotFoundForMalformedSessionId() {
        AtomicReference<CurrentUser> captured = new AtomicReference<>();
        FakeQuizApi quizApi = new FakeQuizApi(captured, List.of(), null);
        GetQuizTool tool = new GetQuizTool(quizApi);

        GetQuizResult result = tool.getQuiz("not-a-uuid", toolContext(userA));

        assertThat(result.isFound()).isFalse();
        assertThat(captured.get()).isNull();
    }

    @Test
    void getQuizFailsWhenToolContextIsAnonymous() {
        FakeQuizApi quizApi = new FakeQuizApi(new AtomicReference<>(), List.of(), null);
        GetQuizTool tool = new GetQuizTool(quizApi);

        assertThatThrownBy(() -> tool.getQuiz(UUID.randomUUID().toString(), null))
                .isInstanceOf(IllegalStateException.class);
    }

    private QuizQuestionDto question(Boolean correct) {
        return new QuizQuestionDto(UUID.randomUUID(), "concept", UUID.randomUUID(), correct, OffsetDateTime.now());
    }

    private ToolContext toolContext(UUID userId) {
        return new ToolContext(Map.of(AgentToolContext.CURRENT_USER_KEY, new CurrentUser(userId)));
    }

    private static final class FakeQuizApi implements QuizApi {

        private final AtomicReference<CurrentUser> captured;
        private final List<QuizSessionDto> sessions;
        private final QuizSessionDto session;

        private FakeQuizApi(AtomicReference<CurrentUser> captured, List<QuizSessionDto> sessions,
                            QuizSessionDto session) {
            this.captured = captured;
            this.sessions = sessions;
            this.session = session;
        }

        @Override
        public List<QuizQuestionDto> findIncorrectQuestionsForUser(UUID userId) {
            return List.of();
        }

        @Override
        public List<QuizSessionDto> findAllSessionsForUser(UUID userId) {
            return sessions;
        }

        @Override
        public Optional<QuizSessionDto> findSessionForUser(CurrentUser currentUser, UUID sessionId) {
            captured.set(currentUser);
            if (session != null && session.getId().equals(sessionId)) {
                return Optional.of(session);
            }
            return Optional.empty();
        }

        @Override
        public QuizCardDto startQuiz(CurrentUser currentUser, String scope, Integer questionCount, String difficulty) {
            return null;
        }

        @Override
        public QuizGradeDto gradeAnswer(CurrentUser currentUser, UUID sessionId, int questionNumber, String answer) {
            return null;
        }

        @Override
        public QuizPollSpecDto resolveQuizPoll(CurrentUser currentUser, UUID sessionId, int questionNumber) {
            return null;
        }

        @Override
        public WeeklyQuizStats weeklyStats(UUID userId, OffsetDateTime since) {
            return new WeeklyQuizStats(0, 0, 0);
        }
    }
}
