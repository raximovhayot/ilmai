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
import org.aiincubator.ilmai.quiz.domain.QuestionType;
import org.aiincubator.ilmai.quiz.domain.QuizSession;
import org.aiincubator.ilmai.quiz.domain.QuizSessionRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuizServiceCitationTest {

    @Mock QuizSessionRepository sessions;
    @Mock MaterialsApi materialsApi;
    @Mock ProfilesApi profilesApi;
    @Mock RetrievalApi retrievalApi;
    @Mock QuizGenerator quizGenerator;
    @Mock QuizGrader quizGrader;
    @Mock QuotaService quotaService;
    @Mock QuizMapper quizMapper;
    @Mock ApplicationEventPublisher events;

    @InjectMocks QuizService quizService;

    @Test
    void start_persistsEveryQuestionWithAnOwnedSourceCitation() {
        UUID userId = UUID.randomUUID();
        UUID ownedMaterial = UUID.randomUUID();
        UUID unownedMaterial = UUID.randomUUID();
        UUID chunkMaterial = UUID.randomUUID();
        CurrentUser currentUser = new CurrentUser(userId);

        when(quotaService.dailyQuizQuota(userId)).thenReturn(0);
        when(profilesApi.require(userId)).thenReturn(profile(userId));
        when(materialsApi.hasReadyMaterialsForUser(userId)).thenReturn(true);
        when(retrievalApi.retrieve(eq(userId), anyString())).thenReturn(List.of(
                new RetrievedChunkDto(chunkMaterial, "Notes", 2, "content", 0.9)));
        when(quizGenerator.generate(any(), any(), anyInt(), anyList())).thenReturn(List.of(
                new QuestionDraft(QuestionType.MULTIPLE_CHOICE, "c1", "p1",
                        List.of("a", "b", "c", "d"), "a", "e1", ownedMaterial, "Notes", 2),
                new QuestionDraft(QuestionType.SHORT_ANSWER, "c2", "p2",
                        List.of(), "ans", "e2", unownedMaterial, "Other", 0),
                new QuestionDraft(QuestionType.OPEN_ENDED, "c3", "p3",
                        List.of(), "ans", "e3", null, null, null)));
        when(materialsApi.findOwnedByUser(ownedMaterial, userId)).thenReturn(Optional.of(material(ownedMaterial)));
        when(materialsApi.findOwnedByUser(unownedMaterial, userId)).thenReturn(Optional.empty());
        when(sessions.save(any(QuizSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(quizMapper.toResponse(any(QuizSession.class))).thenReturn(new QuizSessionResponse());

        quizService.start(currentUser, new StartQuizRequest());

        QuizSession persisted = capturePersistedSession();
        assertThat(persisted.getQuestions()).hasSize(3);
        assertThat(persisted.getQuestions()).allSatisfy(q -> assertThat(q.getMaterialId()).isNotNull());
        assertThat(persisted.getQuestions()).noneSatisfy(q -> assertThat(q.getMaterialId()).isEqualTo(unownedMaterial));
        assertThat(persisted.getQuestions().get(0).getMaterialId()).isEqualTo(ownedMaterial);
        assertThat(persisted.getQuestions().get(1).getMaterialId()).isEqualTo(chunkMaterial);
        assertThat(persisted.getQuestions().get(2).getMaterialId()).isEqualTo(chunkMaterial);
        assertThat(persisted.getTotalCount()).isEqualTo(3);
    }

    @Test
    void start_dropsQuestionWhenNoOwnedSourceCitationIsAvailable() {
        UUID userId = UUID.randomUUID();
        UUID unownedMaterial = UUID.randomUUID();
        CurrentUser currentUser = new CurrentUser(userId);

        when(quotaService.dailyQuizQuota(userId)).thenReturn(0);
        when(profilesApi.require(userId)).thenReturn(profile(userId));
        when(materialsApi.hasReadyMaterialsForUser(userId)).thenReturn(true);
        when(retrievalApi.retrieve(eq(userId), anyString())).thenReturn(List.of());
        when(materialsApi.findReadyForUser(userId)).thenReturn(List.of());
        when(quizGenerator.generate(any(), any(), anyInt(), anyList())).thenReturn(List.of(
                new QuestionDraft(QuestionType.SHORT_ANSWER, "c", "p",
                        List.of(), "ans", "e", unownedMaterial, "Other", 0)));
        when(materialsApi.findOwnedByUser(unownedMaterial, userId)).thenReturn(Optional.empty());
        when(sessions.save(any(QuizSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(quizMapper.toResponse(any(QuizSession.class))).thenReturn(new QuizSessionResponse());

        quizService.start(currentUser, new StartQuizRequest());

        QuizSession persisted = capturePersistedSession();
        assertThat(persisted.getQuestions()).isEmpty();
        assertThat(persisted.getTotalCount()).isZero();
    }

    private QuizSession capturePersistedSession() {
        ArgumentCaptor<QuizSession> captor = ArgumentCaptor.forClass(QuizSession.class);
        verify(sessions, atLeastOnce()).save(captor.capture());
        return captor.getValue();
    }

    private ProfileDto profile(UUID userId) {
        return new ProfileDto(userId, SupportedLocale.EN, "Asia/Tashkent", "general",
                null, null, null, 0, 0, 0, null);
    }

    private MaterialDto material(UUID id) {
        return new MaterialDto(id, null, null, "Notes", "application/pdf", 10L, MaterialStatus.READY, 0, null, null);
    }
}
