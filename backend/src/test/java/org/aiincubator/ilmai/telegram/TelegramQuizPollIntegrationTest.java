package org.aiincubator.ilmai.telegram;

import org.aiincubator.ilmai.ai.ingestion.support.IntegrationTestConfiguration;
import org.aiincubator.ilmai.auth.UserStatus;
import org.aiincubator.ilmai.auth.domain.User;
import org.aiincubator.ilmai.auth.domain.UserRepository;
import org.aiincubator.ilmai.common.i18n.SupportedLocale;
import org.aiincubator.ilmai.quiz.domain.QuestionType;
import org.aiincubator.ilmai.quiz.domain.QuizDifficulty;
import org.aiincubator.ilmai.quiz.domain.QuizQuestion;
import org.aiincubator.ilmai.quiz.domain.QuizSession;
import org.aiincubator.ilmai.quiz.domain.QuizSessionRepository;
import org.aiincubator.ilmai.quiz.domain.QuizStatus;
import org.aiincubator.ilmai.telegram.domain.TelegramLink;
import org.aiincubator.ilmai.telegram.domain.TelegramLinkRepository;
import org.aiincubator.ilmai.telegram.domain.TelegramQuizPoll;
import org.aiincubator.ilmai.telegram.domain.TelegramQuizPollRepository;
import org.aiincubator.ilmai.telegram.service.TelegramApiClient;
import org.aiincubator.ilmai.telegram.service.TelegramUpdateHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.polls.PollAnswer;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@Import(IntegrationTestConfiguration.class)
@TestPropertySource(properties = {
        "ai.embedding.api-key=integration-test-key",
        "spring.ai.google.genai.api-key=integration-test-google-genai-key",
        "spring.docker.compose.enabled=false",
        "auth.jwt.secret=integration-test-jwt-secret-32-chars-long-x",
        "auth.google.client-id=integration-test-google-client-id"
})
class TelegramQuizPollIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("ilmai_test")
            .withUsername("test")
            .withPassword("test");

    @MockitoBean
    private TelegramApiClient telegramApiClient;

    @Autowired
    private TelegramUpdateHandler handler;

    @Autowired
    private UserRepository users;

    @Autowired
    private TelegramLinkRepository links;

    @Autowired
    private QuizSessionRepository quizSessions;

    @Autowired
    private TelegramQuizPollRepository polls;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void telegramMcqPollVoteGradesAnswerAndRepliesToTheBoundUser() {
        long chatId = 222L;
        UUID userId = seedUser();
        linkChat(userId, chatId);
        UUID sessionId = seedMcqSession(userId);
        seedPollBinding("poll-rt", userId, chatId, sessionId);

        handler.handleUpdate(pollAnswer("poll-rt", 0));

        verify(telegramApiClient, times(1)).sendMessage(eq(chatId), anyString());

        transactionTemplate.executeWithoutResult(status -> {
            QuizSession graded = quizSessions.findById(sessionId).orElseThrow();
            QuizQuestion question = graded.getQuestions().get(0);
            assertThat(question.getIsCorrect()).isTrue();
            assertThat(question.getUserAnswer()).isEqualTo("Paris");
        });

        TelegramQuizPoll binding = polls.findByPollId("poll-rt").orElseThrow();
        assertThat(binding.getAnsweredAt()).isNotNull();
    }

    @Test
    void telegramMcqPollWrongVoteIsGradedIncorrect() {
        long chatId = 333L;
        UUID userId = seedUser();
        linkChat(userId, chatId);
        UUID sessionId = seedMcqSession(userId);
        seedPollBinding("poll-wrong", userId, chatId, sessionId);

        handler.handleUpdate(pollAnswer("poll-wrong", 1));

        verify(telegramApiClient, times(1)).sendMessage(eq(chatId), anyString());

        transactionTemplate.executeWithoutResult(status -> {
            QuizSession graded = quizSessions.findById(sessionId).orElseThrow();
            QuizQuestion question = graded.getQuestions().get(0);
            assertThat(question.getIsCorrect()).isFalse();
            assertThat(question.getUserAnswer()).isEqualTo("London");
        });
    }

    private UUID seedUser() {
        return transactionTemplate.execute(status -> {
            User user = new User();
            user.setUsername("user-" + UUID.randomUUID() + "@example.com");
            user.setStatus(UserStatus.ACTIVE);
            return users.saveAndFlush(user).getId();
        });
    }

    private void linkChat(UUID userId, long chatId) {
        transactionTemplate.executeWithoutResult(status -> {
            TelegramLink link = new TelegramLink();
            link.setUserId(userId);
            link.setChatId(chatId);
            link.setLinkedAt(OffsetDateTime.parse("2026-05-01T00:00:00Z"));
            links.saveAndFlush(link);
        });
    }

    private UUID seedMcqSession(UUID userId) {
        return transactionTemplate.execute(status -> {
            QuizSession session = new QuizSession();
            session.setUserId(userId);
            session.setRoomId(UUID.randomUUID());
            session.setDifficulty(QuizDifficulty.SOLID);
            session.setDifficultyLevel(2);
            session.setLocale(SupportedLocale.EN);
            session.setStatus(QuizStatus.IN_PROGRESS);
            session.setTotalCount(1);
            session.setCorrectCount(0);

            QuizQuestion question = new QuizQuestion();
            question.setSession(session);
            question.setPosition(1);
            question.setType(QuestionType.MULTIPLE_CHOICE);
            question.setConcept("Geography");
            question.setPrompt("What is the capital of France?");
            question.setOptions(List.of("Paris", "London"));
            question.setCorrectAnswer("Paris");
            question.setExplanation("Paris is the capital of France.");
            session.getQuestions().add(question);

            return quizSessions.saveAndFlush(session).getId();
        });
    }

    private void seedPollBinding(String pollId, UUID userId, long chatId, UUID sessionId) {
        transactionTemplate.executeWithoutResult(status -> {
            TelegramQuizPoll binding = new TelegramQuizPoll();
            binding.setPollId(pollId);
            binding.setUserId(userId);
            binding.setChatId(chatId);
            binding.setSessionId(sessionId);
            binding.setQuestionId(UUID.randomUUID());
            binding.setPosition(1);
            binding.setOptions(List.of("Paris", "London"));
            polls.saveAndFlush(binding);
        });
    }

    private Update pollAnswer(String pollId, int optionId) {
        PollAnswer pollAnswer = new PollAnswer();
        pollAnswer.setPollId(pollId);
        pollAnswer.setOptionIds(List.of(optionId));
        Update request = new Update();
        request.setPollAnswer(pollAnswer);
        return request;
    }
}
