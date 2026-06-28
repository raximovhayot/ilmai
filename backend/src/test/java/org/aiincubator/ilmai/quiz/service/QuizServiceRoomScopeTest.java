package org.aiincubator.ilmai.quiz.service;

import org.aiincubator.ilmai.ai.RetrievalApi;
import org.aiincubator.ilmai.common.CurrentUser;
import org.aiincubator.ilmai.common.i18n.SupportedLocale;
import org.aiincubator.ilmai.common.quota.QuotaService;
import org.aiincubator.ilmai.materials.MaterialsApi;
import org.aiincubator.ilmai.profiles.ProfileDto;
import org.aiincubator.ilmai.profiles.ProfilesApi;
import org.aiincubator.ilmai.quiz.domain.QuizSession;
import org.aiincubator.ilmai.quiz.domain.QuizSessionRepository;
import org.aiincubator.ilmai.quiz.payload.QuizSessionResponse;
import org.aiincubator.ilmai.quiz.payload.StartQuizRequest;
import org.aiincubator.ilmai.rooms.RoomDto;
import org.aiincubator.ilmai.rooms.RoomsApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
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
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class QuizServiceRoomScopeTest {

    @Mock QuizSessionRepository sessions;
    @Mock MaterialsApi materialsApi;
    @Mock ProfilesApi profilesApi;
    @Mock RoomsApi roomsApi;
    @Mock RetrievalApi retrievalApi;
    @Mock QuizGenerator quizGenerator;
    @Mock QuizGrader quizGrader;
    @Mock QuotaService quotaService;
    @Mock QuizMapper quizMapper;
    @Mock ApplicationEventPublisher events;

    @InjectMocks QuizService quizService;

    @Test
    void start_defaultsToPersonalRoomWhenNoRoomRequested() {
        UUID userId = UUID.randomUUID();
        UUID personalRoomId = UUID.randomUUID();
        CurrentUser currentUser = new CurrentUser(userId);
        stubStartHappyPath(userId);
        when(roomsApi.findPersonalForUser(userId))
                .thenReturn(Optional.of(new RoomDto(personalRoomId, userId, "Personal", true)));

        quizService.start(currentUser, new StartQuizRequest());

        assertThat(capturePersistedSession().getRoomId()).isEqualTo(personalRoomId);
        verify(roomsApi, never()).requireMember(any(CurrentUser.class), any(UUID.class));
    }

    @Test
    void start_stampsRequestedRoomAfterMembershipCheck() {
        UUID userId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        CurrentUser currentUser = new CurrentUser(userId);
        stubStartHappyPath(userId);
        when(roomsApi.requireMember(currentUser, roomId))
                .thenReturn(new RoomDto(roomId, UUID.randomUUID(), "Shared", false));

        StartQuizRequest request = new StartQuizRequest();
        request.setRoomId(roomId);
        quizService.start(currentUser, request);

        assertThat(capturePersistedSession().getRoomId()).isEqualTo(roomId);
        verify(roomsApi).requireMember(currentUser, roomId);
        verify(roomsApi, never()).findPersonalForUser(any(UUID.class));
    }

    @Test
    void start_rejectsRoomWhenCallerIsNotMember() {
        UUID userId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        CurrentUser currentUser = new CurrentUser(userId);
        when(quotaService.dailyQuizQuota(userId)).thenReturn(0);
        when(profilesApi.require(userId)).thenReturn(profile(userId));
        when(materialsApi.hasReadyMaterialsForUser(userId)).thenReturn(true);
        when(roomsApi.requireMember(currentUser, roomId)).thenThrow(new IllegalStateException("not a member"));

        StartQuizRequest request = new StartQuizRequest();
        request.setRoomId(roomId);
        assertThatThrownBy(() -> quizService.start(currentUser, request))
                .isInstanceOf(IllegalStateException.class);
        verify(sessions, never()).save(any(QuizSession.class));
    }

    private void stubStartHappyPath(UUID userId) {
        when(quotaService.dailyQuizQuota(userId)).thenReturn(0);
        when(profilesApi.require(userId)).thenReturn(profile(userId));
        when(materialsApi.hasReadyMaterialsForUser(userId)).thenReturn(true);
        when(retrievalApi.retrieve(any(UUID.class), any(), anyString())).thenReturn(List.of());
        when(materialsApi.findReadyForUser(userId)).thenReturn(List.of());
        when(quizGenerator.generate(any(), any(), anyInt(), anyList())).thenReturn(List.of());
        when(sessions.save(any(QuizSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(quizMapper.toResponse(any(QuizSession.class))).thenReturn(new QuizSessionResponse());
    }

    private QuizSession capturePersistedSession() {
        ArgumentCaptor<QuizSession> captor = ArgumentCaptor.forClass(QuizSession.class);
        verify(sessions, atLeastOnce()).save(captor.capture());
        return captor.getValue();
    }

    private ProfileDto profile(UUID userId) {
        return new ProfileDto(userId, SupportedLocale.EN, "Asia/Tashkent", null, 0, 0, 0, null);
    }
}
