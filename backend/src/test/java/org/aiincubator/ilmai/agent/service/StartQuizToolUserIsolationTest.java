package org.aiincubator.ilmai.agent.service;

import org.aiincubator.ilmai.common.CurrentUser;
import org.aiincubator.ilmai.quiz.QuizApi;
import org.aiincubator.ilmai.quiz.QuizCardDto;
import org.aiincubator.ilmai.quiz.QuizCardQuestionDto;
import org.aiincubator.ilmai.quiz.QuizGradeDto;
import org.aiincubator.ilmai.quiz.QuizPollSpecDto;
import org.aiincubator.ilmai.quiz.QuizQuestionDto;
import org.aiincubator.ilmai.quiz.QuizSessionDto;
import org.aiincubator.ilmai.quiz.QuizUnavailableException;
import org.aiincubator.ilmai.quiz.QuizUnavailableReason;
import org.aiincubator.ilmai.quiz.WeeklyQuizStats;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StartQuizToolUserIsolationTest {

    private final UUID userA = UUID.randomUUID();

    @AfterEach
    void tearDown() {
        AgentQuizContext.clear();
    }

    @Test
    void startQuizUsesUserFromToolContext_andRecordsCardIntoTurnContext() {
        UUID sessionId = UUID.randomUUID();
        AtomicReference<CurrentUser> captured = new AtomicReference<>();
        QuizApi quizApi = new CapturingQuizApi(captured, cardFor(sessionId), null);
        StartQuizTool tool = new StartQuizTool(quizApi);

        AgentQuizContext ctx = AgentQuizContext.begin();
        ToolContext toolContext = new ToolContext(Map.of(AgentToolContext.CURRENT_USER_KEY, new CurrentUser(userA)));

        StartQuizResult result = tool.startQuiz("photosynthesis", 5, "solid", "single", toolContext);

        assertThat(captured.get()).isNotNull();
        assertThat(captured.get().getUserId()).isEqualTo(userA);
        assertThat(result.isCreated()).isTrue();
        assertThat(result.getSessionId()).isEqualTo(sessionId);
        assertThat(result.getQuestionCount()).isEqualTo(1);
        assertThat(ctx.cards()).hasSize(1);
        assertThat(ctx.cards().get(0).getSessionId()).isEqualTo(sessionId);
    }

    @Test
    void startQuizFailsWhenToolContextIsAnonymous() {
        QuizApi quizApi = new CapturingQuizApi(new AtomicReference<>(), cardFor(UUID.randomUUID()), null);
        StartQuizTool tool = new StartQuizTool(quizApi);

        assertThatThrownBy(() -> tool.startQuiz("anything", null, null, null, null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void startQuizReturnsNotCreatedWhenQuizUnavailable() {
        QuizApi quizApi = new CapturingQuizApi(new AtomicReference<>(), null,
                new QuizUnavailableException(QuizUnavailableReason.MATERIALS_MISSING));
        StartQuizTool tool = new StartQuizTool(quizApi);

        AgentQuizContext ctx = AgentQuizContext.begin();
        ToolContext toolContext = new ToolContext(Map.of(AgentToolContext.CURRENT_USER_KEY, new CurrentUser(userA)));

        StartQuizResult result = tool.startQuiz(null, null, null, null, toolContext);

        assertThat(result.isCreated()).isFalse();
        assertThat(result.getReason()).isEqualTo("materials_missing");
        assertThat(ctx.cards()).isEmpty();
    }

    private QuizCardDto cardFor(UUID sessionId) {
        return new QuizCardDto(sessionId, null, "SOLID", "EN", List.of(
                new QuizCardQuestionDto(UUID.randomUUID(), 1, "MULTIPLE_CHOICE", "concept", "prompt?",
                        List.of("a", "b", "c", "d"), UUID.randomUUID(), "Notes", 2)));
    }


    private static final class CapturingQuizApi implements QuizApi {

        private final AtomicReference<CurrentUser> captured;
        private final QuizCardDto card;
        private final QuizUnavailableException failure;

        private CapturingQuizApi(AtomicReference<CurrentUser> captured, QuizCardDto card,
                                 QuizUnavailableException failure) {
            this.captured = captured;
            this.card = card;
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
            return java.util.Optional.empty();
        }

        @Override
        public QuizCardDto startQuiz(CurrentUser currentUser, String scope, Integer questionCount, String difficulty) {
            captured.set(currentUser);
            if (failure != null) {
                throw failure;
            }
            return card;
        }

        @Override
        public QuizGradeDto gradeAnswer(CurrentUser currentUser, UUID sessionId, int questionNumber, String answer) {
            captured.set(currentUser);
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
