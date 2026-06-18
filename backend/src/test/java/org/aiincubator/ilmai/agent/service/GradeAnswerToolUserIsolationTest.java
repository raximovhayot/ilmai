package org.aiincubator.ilmai.agent.service;

import org.aiincubator.ilmai.common.CurrentUser;
import org.aiincubator.ilmai.quiz.QuizApi;
import org.aiincubator.ilmai.quiz.QuizCardDto;
import org.aiincubator.ilmai.quiz.QuizGradeDto;
import org.aiincubator.ilmai.quiz.QuizGradeException;
import org.aiincubator.ilmai.quiz.QuizGradeReason;
import org.aiincubator.ilmai.quiz.QuizPollSpecDto;
import org.aiincubator.ilmai.quiz.QuizQuestionDto;
import org.aiincubator.ilmai.quiz.QuizSessionDto;
import org.aiincubator.ilmai.quiz.WeeklyQuizStats;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GradeAnswerToolUserIsolationTest {

    private final UUID userA = UUID.randomUUID();

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void gradeAnswerUsesUserFromSecurityContext_andReturnsGradedResult() {
        UUID sessionId = UUID.randomUUID();
        AtomicReference<CurrentUser> captured = new AtomicReference<>();
        AtomicReference<UUID> capturedSession = new AtomicReference<>();
        QuizGradeDto dto = new QuizGradeDto(true, "Correct!", "B", "because B", "concept",
                2, 1, 5, 1, false, 3);
        QuizApi quizApi = new CapturingQuizApi(captured, capturedSession, dto, null);
        GradeAnswerTool tool = new GradeAnswerTool(quizApi);

        authenticate(userA);

        GradeAnswerResult result = tool.gradeAnswer(sessionId.toString(), 2, "B", new ToolContext(Map.of(AgentToolContext.CURRENT_USER_KEY, new CurrentUser(userA))));

        assertThat(captured.get()).isNotNull();
        assertThat(captured.get().getUserId()).isEqualTo(userA);
        assertThat(capturedSession.get()).isEqualTo(sessionId);
        assertThat(result.isGraded()).isTrue();
        assertThat(result.getResult().getCorrect()).isTrue();
        assertThat(result.getResult().getQuestionNumber()).isEqualTo(2);
        assertThat(result.getResult().getDifficultyLevel()).isEqualTo(3);
        assertThat(result.getReason()).isNull();
    }

    @Test
    void gradeAnswerFailsWhenSecurityContextIsAnonymous() {
        QuizApi quizApi = new CapturingQuizApi(new AtomicReference<>(), new AtomicReference<>(), null, null);
        GradeAnswerTool tool = new GradeAnswerTool(quizApi);

        assertThatThrownBy(() -> tool.gradeAnswer(UUID.randomUUID().toString(), 1, "x", null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void gradeAnswerReturnsNotGradedWhenQuizGradeFails() {
        QuizApi quizApi = new CapturingQuizApi(new AtomicReference<>(), new AtomicReference<>(), null,
                new QuizGradeException(QuizGradeReason.ALREADY_ANSWERED));
        GradeAnswerTool tool = new GradeAnswerTool(quizApi);

        authenticate(userA);

        GradeAnswerResult result = tool.gradeAnswer(UUID.randomUUID().toString(), 1, "x", new ToolContext(Map.of(AgentToolContext.CURRENT_USER_KEY, new CurrentUser(userA))));

        assertThat(result.isGraded()).isFalse();
        assertThat(result.getResult()).isNull();
        assertThat(result.getReason()).isEqualTo("already_answered");
    }

    @Test
    void gradeAnswerReturnsSessionNotFoundForMalformedSessionId() {
        AtomicReference<CurrentUser> captured = new AtomicReference<>();
        QuizApi quizApi = new CapturingQuizApi(captured, new AtomicReference<>(), null, null);
        GradeAnswerTool tool = new GradeAnswerTool(quizApi);

        authenticate(userA);

        GradeAnswerResult result = tool.gradeAnswer("not-a-uuid", 1, "x", new ToolContext(Map.of(AgentToolContext.CURRENT_USER_KEY, new CurrentUser(userA))));

        assertThat(result.isGraded()).isFalse();
        assertThat(result.getReason()).isEqualTo("session_not_found");
        assertThat(captured.get()).isNull();
    }

    private void authenticate(UUID userId) {
        CurrentUser principal = new CurrentUser(userId);
        TestingAuthenticationToken auth = new TestingAuthenticationToken(principal, null);
        auth.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private static final class CapturingQuizApi implements QuizApi {

        private final AtomicReference<CurrentUser> captured;
        private final AtomicReference<UUID> capturedSession;
        private final QuizGradeDto result;
        private final QuizGradeException failure;

        private CapturingQuizApi(AtomicReference<CurrentUser> captured, AtomicReference<UUID> capturedSession,
                                 QuizGradeDto result, QuizGradeException failure) {
            this.captured = captured;
            this.capturedSession = capturedSession;
            this.result = result;
            this.failure = failure;
        }

        @Override
        public List<QuizQuestionDto> findIncorrectQuestionsForUser(UUID userId) {
            return List.of();
        }

        @Override
        public List<QuizSessionDto> findAllSessionsForUser(UUID userId) {
            return List.of();
        }

        @Override
        public java.util.Optional<QuizSessionDto> findSessionForUser(CurrentUser currentUser, UUID sessionId) {
            captured.set(currentUser);
            capturedSession.set(sessionId);
            return java.util.Optional.empty();
        }

        @Override
        public QuizCardDto startQuiz(CurrentUser currentUser, String scope, Integer questionCount, String difficulty) {
            return null;
        }

        @Override
        public QuizGradeDto gradeAnswer(CurrentUser currentUser, UUID sessionId, int questionNumber, String answer) {
            captured.set(currentUser);
            capturedSession.set(sessionId);
            if (failure != null) {
                throw failure;
            }
            return result;
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
