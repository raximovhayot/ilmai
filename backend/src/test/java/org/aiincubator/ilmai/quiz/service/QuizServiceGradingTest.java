package org.aiincubator.ilmai.quiz.service;

import org.aiincubator.ilmai.ai.RetrievalApi;
import org.aiincubator.ilmai.ai.RetrievedChunkDto;
import org.aiincubator.ilmai.common.CurrentUser;
import org.aiincubator.ilmai.common.i18n.SupportedLocale;
import org.aiincubator.ilmai.common.quota.QuotaService;
import org.aiincubator.ilmai.materials.MaterialDto;
import org.aiincubator.ilmai.materials.MaterialStatus;
import org.aiincubator.ilmai.materials.MaterialsApi;
import org.aiincubator.ilmai.profiles.ProfileDto;
import org.aiincubator.ilmai.profiles.ProfilesApi;
import org.aiincubator.ilmai.quiz.QuizAnswerGradedEvent;
import org.aiincubator.ilmai.quiz.domain.QuestionType;
import org.aiincubator.ilmai.quiz.domain.QuizDifficulty;
import org.aiincubator.ilmai.quiz.domain.QuizQuestion;
import org.aiincubator.ilmai.quiz.domain.QuizSession;
import org.aiincubator.ilmai.quiz.domain.QuizSessionRepository;
import org.aiincubator.ilmai.quiz.domain.QuizStatus;
import org.aiincubator.ilmai.quiz.payload.QuizSessionResponse;
import org.aiincubator.ilmai.quiz.payload.StartQuizRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuizServiceGradingTest {

    @Mock QuizSessionRepository sessions;
    @Mock MaterialsApi materialsApi;
    @Mock ProfilesApi profilesApi;
    @Mock RetrievalApi retrievalApi;
    @Mock QuizGenerator quizGenerator;
    @Mock QuizGrader quizGrader;
    @Mock QuizMapper quizMapper;
    @Mock QuotaService quotaService;
    @Mock ApplicationEventPublisher events;

    @InjectMocks QuizService quizService;

    private final UUID userId = UUID.randomUUID();
    private final UUID sessionId = UUID.randomUUID();
    private final CurrentUser currentUser = new CurrentUser(userId);

    @Test
    void start_seedsDifficultyLevelFromChosenDifficulty() {
        UUID ownedMaterial = UUID.randomUUID();
        StartQuizRequest request = new StartQuizRequest();
        request.setDifficulty("expert");

        when(quotaService.dailyQuizQuota(userId)).thenReturn(0);
        when(profilesApi.require(userId)).thenReturn(profile());
        when(materialsApi.hasReadyMaterialsForUser(userId)).thenReturn(true);
        when(retrievalApi.retrieve(eq(userId), anyString())).thenReturn(List.of(
                new RetrievedChunkDto(ownedMaterial, "Notes", 1, "content", 0.8)));
        when(quizGenerator.generate(any(), any(), anyInt(), anyList())).thenReturn(List.of(
                new QuestionDraft(QuestionType.MULTIPLE_CHOICE, "c1", "p1",
                        List.of("a", "b", "c", "d"), "a", "e1", ownedMaterial, "Notes", 1)));
        when(materialsApi.findOwnedByUser(ownedMaterial, userId)).thenReturn(Optional.of(material(ownedMaterial)));
        when(sessions.save(any(QuizSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(quizMapper.toResponse(any(QuizSession.class))).thenReturn(new QuizSessionResponse());

        quizService.start(currentUser, request);

        assertThat(capturePersistedSession().getDifficultyLevel()).isEqualTo(3);
    }

    @Test
    void gradeByPosition_correctAnswer_raisesDifficultyLevel_andReturnsOutcome() {
        QuizSession session = sessionWith(QuizDifficulty.SOLID, 2, 2,
                question(1, "a"), question(2, "b"));
        when(sessions.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.of(session));
        when(quizGrader.grade(any(QuizQuestion.class), anyString())).thenReturn(new QuizGradeResult(true, "Correct!"));

        QuizGradeOutcome outcome = quizService.gradeByPosition(currentUser, sessionId, 1, "a");

        assertThat(outcome.getCorrect()).isTrue();
        assertThat(outcome.getFeedback()).isEqualTo("Correct!");
        assertThat(outcome.getCorrectAnswer()).isEqualTo("a");
        assertThat(outcome.getExplanation()).isEqualTo("explanation1");
        assertThat(outcome.getQuestionNumber()).isEqualTo(1);
        assertThat(outcome.getAnsweredCount()).isEqualTo(1);
        assertThat(outcome.getTotalCount()).isEqualTo(2);
        assertThat(outcome.getCorrectCount()).isEqualTo(1);
        assertThat(outcome.isCompleted()).isFalse();
        assertThat(outcome.getDifficultyLevel()).isEqualTo(3);
        assertThat(session.getDifficultyLevel()).isEqualTo(3);
    }

    @Test
    void gradeByPosition_wrongAnswer_lowersDifficultyLevel() {
        QuizSession session = sessionWith(QuizDifficulty.SOLID, 3, 2,
                question(1, "a"), question(2, "b"));
        when(sessions.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.of(session));
        when(quizGrader.grade(any(QuizQuestion.class), anyString())).thenReturn(new QuizGradeResult(false, "Not quite."));

        QuizGradeOutcome outcome = quizService.gradeByPosition(currentUser, sessionId, 1, "z");

        assertThat(outcome.getCorrect()).isFalse();
        assertThat(outcome.getCorrectCount()).isZero();
        assertThat(outcome.getDifficultyLevel()).isEqualTo(2);
    }

    @Test
    void gradeByPosition_wrongAnswer_clampsDifficultyLevelAtMin() {
        QuizSession session = sessionWith(QuizDifficulty.GENTLE, 1, 2,
                question(1, "a"), question(2, "b"));
        when(sessions.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.of(session));
        when(quizGrader.grade(any(QuizQuestion.class), anyString())).thenReturn(new QuizGradeResult(false, "Not quite."));

        QuizGradeOutcome outcome = quizService.gradeByPosition(currentUser, sessionId, 1, "z");

        assertThat(outcome.getDifficultyLevel()).isEqualTo(1);
    }

    @Test
    void gradeByPosition_lastCorrectAnswer_clampsAtMax_andCompletesSession() {
        QuizSession session = sessionWith(QuizDifficulty.EXPERT, 5, 1, question(1, "a"));
        when(sessions.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.of(session));
        when(quizGrader.grade(any(QuizQuestion.class), anyString())).thenReturn(new QuizGradeResult(true, "Correct!"));

        QuizGradeOutcome outcome = quizService.gradeByPosition(currentUser, sessionId, 1, "a");

        assertThat(outcome.getDifficultyLevel()).isEqualTo(5);
        assertThat(outcome.isCompleted()).isTrue();
        assertThat(session.getStatus()).isEqualTo(QuizStatus.COMPLETED);
        assertThat(session.getScore()).isEqualTo(1.0);
    }

    @Test
    void gradeByPosition_alreadyAnsweredQuestion_throws() {
        QuizQuestion answered = question(1, "a");
        answered.setIsCorrect(Boolean.TRUE);
        QuizSession session = sessionWith(QuizDifficulty.SOLID, 2, 2, answered, question(2, "b"));
        when(sessions.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> quizService.gradeByPosition(currentUser, sessionId, 1, "a"))
                .isInstanceOf(QuizException.class)
                .extracting(e -> ((QuizException) e).getReason())
                .isEqualTo(QuizException.Reason.QUIZ_ALREADY_ANSWERED);
    }

    @Test
    void gradeByPosition_unknownPosition_throwsQuestionNotFound() {
        QuizSession session = sessionWith(QuizDifficulty.SOLID, 2, 1, question(1, "a"));
        when(sessions.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> quizService.gradeByPosition(currentUser, sessionId, 7, "a"))
                .isInstanceOf(QuizException.class)
                .extracting(e -> ((QuizException) e).getReason())
                .isEqualTo(QuizException.Reason.QUIZ_QUESTION_NOT_FOUND);
    }

    @Test
    void gradeByPosition_unknownSession_throwsNotFound() {
        when(sessions.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> quizService.gradeByPosition(currentUser, sessionId, 1, "a"))
                .isInstanceOf(QuizException.class)
                .extracting(e -> ((QuizException) e).getReason())
                .isEqualTo(QuizException.Reason.QUIZ_NOT_FOUND);
    }

    @Test
    void gradeByPosition_wrongAnswer_publishesGradedEventWithCorrectFalse() {
        QuizSession session = sessionWith(QuizDifficulty.SOLID, 3, 2,
                question(1, "a"), question(2, "b"));
        UUID materialId = UUID.randomUUID();
        session.getQuestions().get(0).setMaterialId(materialId);
        when(sessions.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.of(session));
        when(quizGrader.grade(any(QuizQuestion.class), anyString())).thenReturn(new QuizGradeResult(false, "Not quite."));

        quizService.gradeByPosition(currentUser, sessionId, 1, "z");

        ArgumentCaptor<QuizAnswerGradedEvent> captor = ArgumentCaptor.forClass(QuizAnswerGradedEvent.class);
        verify(events).publishEvent(captor.capture());
        QuizAnswerGradedEvent event = captor.getValue();
        assertThat(event.getUserId()).isEqualTo(userId);
        assertThat(event.getSessionId()).isEqualTo(sessionId);
        assertThat(event.getQuestionId()).isNotNull();
        assertThat(event.getConcept()).isEqualTo("concept1");
        assertThat(event.getMaterialId()).isEqualTo(materialId);
        assertThat(event.isCorrect()).isFalse();
        assertThat(event.getOccurredAt()).isNotNull();
    }

    @Test
    void gradeByPosition_correctAnswer_publishesGradedEventWithCorrectTrue() {
        QuizSession session = sessionWith(QuizDifficulty.SOLID, 2, 2,
                question(1, "a"), question(2, "b"));
        when(sessions.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.of(session));
        when(quizGrader.grade(any(QuizQuestion.class), anyString())).thenReturn(new QuizGradeResult(true, "Correct!"));

        quizService.gradeByPosition(currentUser, sessionId, 1, "a");

        ArgumentCaptor<QuizAnswerGradedEvent> captor = ArgumentCaptor.forClass(QuizAnswerGradedEvent.class);
        verify(events).publishEvent(captor.capture());
        assertThat(captor.getValue().isCorrect()).isTrue();
        assertThat(captor.getValue().getConcept()).isEqualTo("concept1");
    }

    private QuizSession sessionWith(QuizDifficulty difficulty, int level, int totalCount, QuizQuestion... questions) {
        QuizSession session = new QuizSession();
        session.setId(sessionId);
        session.setUserId(userId);
        session.setDifficulty(difficulty);
        session.setDifficultyLevel(level);
        session.setStatus(QuizStatus.IN_PROGRESS);
        session.setTotalCount(totalCount);
        for (QuizQuestion question : questions) {
            question.setSession(session);
            session.getQuestions().add(question);
        }
        return session;
    }

    private QuizQuestion question(int position, String correctAnswer) {
        QuizQuestion question = new QuizQuestion();
        question.setId(UUID.randomUUID());
        question.setPosition(position);
        question.setType(QuestionType.MULTIPLE_CHOICE);
        question.setConcept("concept" + position);
        question.setPrompt("prompt" + position);
        question.setCorrectAnswer(correctAnswer);
        question.setExplanation("explanation" + position);
        return question;
    }

    private QuizSession capturePersistedSession() {
        ArgumentCaptor<QuizSession> captor = ArgumentCaptor.forClass(QuizSession.class);
        verify(sessions, atLeastOnce()).save(captor.capture());
        return captor.getValue();
    }

    private ProfileDto profile() {
        return new ProfileDto(userId, SupportedLocale.EN, "Asia/Tashkent", "general",
                null, null, null, 0, 0, 0, null);
    }

    private MaterialDto material(UUID id) {
        return new MaterialDto(id, null, null, "Notes", "application/pdf", 10L, MaterialStatus.READY, 0, null, null);
    }
}
