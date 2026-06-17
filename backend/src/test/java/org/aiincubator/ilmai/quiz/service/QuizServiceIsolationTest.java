package org.aiincubator.ilmai.quiz.service;

import org.aiincubator.ilmai.common.CurrentUser;
import org.aiincubator.ilmai.auth.AuthApi;
import org.aiincubator.ilmai.ai.RetrievalApi;
import org.aiincubator.ilmai.common.quota.QuotaService;
import org.aiincubator.ilmai.materials.MaterialsApi;
import org.aiincubator.ilmai.profiles.ProfilesApi;
import org.aiincubator.ilmai.quiz.domain.QuizSessionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuizServiceIsolationTest {

    @Mock QuizSessionRepository sessions;
    @Mock AuthApi authApi;
    @Mock MaterialsApi materialsApi;
    @Mock ProfilesApi profilesApi;
    @Mock RetrievalApi retrievalApi;
    @Mock QuizGenerator quizGenerator;
    @Mock QuizGrader quizGrader;
    @Mock QuotaService quotaService;
    @Mock ApplicationEventPublisher events;
    @Spy QuizMapper quizMapper = Mappers.getMapper(QuizMapper.class);

    @InjectMocks QuizService quizService;

    @Test
    void get_throwsNotFoundWhenSessionBelongsToAnotherUser() {
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        when(sessions.findByIdAndUserId(sessionId, userA)).thenReturn(Optional.empty());

        CurrentUser actingAs = new CurrentUser(userA);
        assertThatThrownBy(() -> quizService.get(actingAs, sessionId))
                .isInstanceOf(QuizException.class)
                .extracting(e -> ((QuizException) e).getReason())
                .isEqualTo(QuizException.Reason.QUIZ_NOT_FOUND);
        verify(sessions, never()).findByIdAndUserId(sessionId, userB);
    }

    @Test
    void answer_throwsNotFoundWhenSessionBelongsToAnotherUser() {
        UUID userA = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID questionId = UUID.randomUUID();
        when(sessions.findByIdAndUserId(sessionId, userA)).thenReturn(Optional.empty());

        CurrentUser actingAs = new CurrentUser(userA);
        assertThatThrownBy(() -> quizService.answer(actingAs, sessionId, questionId, "x"))
                .isInstanceOf(QuizException.class)
                .extracting(e -> ((QuizException) e).getReason())
                .isEqualTo(QuizException.Reason.QUIZ_NOT_FOUND);
    }
}
