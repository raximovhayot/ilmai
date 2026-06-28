package org.aiincubator.ilmai.agent.service;

import org.aiincubator.ilmai.agent.ChatChannel;
import org.aiincubator.ilmai.agent.ChatSessionSummary;
import org.aiincubator.ilmai.agent.api.ChatSessionResponse;
import org.aiincubator.ilmai.agent.api.CreateChatSessionRequest;
import org.aiincubator.ilmai.agent.domain.ChatMemorySummary;
import org.aiincubator.ilmai.agent.domain.ChatMemorySummaryRepository;
import org.aiincubator.ilmai.agent.domain.ChatSession;
import org.aiincubator.ilmai.agent.domain.ChatSessionRepository;
import org.aiincubator.ilmai.common.CurrentUser;
import org.aiincubator.ilmai.rooms.RoomDto;
import org.aiincubator.ilmai.rooms.RoomsApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatSessionServiceTest {

    private ChatSessionRepository repository;
    private ChatMemorySummaryRepository memorySummaries;
    private RoomsApi roomsApi;
    private ChatSessionService service;

    @BeforeEach
    void setUp() {
        repository = mock(ChatSessionRepository.class);
        memorySummaries = mock(ChatMemorySummaryRepository.class);
        roomsApi = mock(RoomsApi.class);
        lenient().when(roomsApi.findPersonalForUser(any(UUID.class))).thenAnswer(invocation -> {
            UUID owner = invocation.getArgument(0);
            return Optional.of(new RoomDto(UUID.randomUUID(), owner, "Personal", true));
        });
        service = new ChatSessionService(
                repository, memorySummaries, Mappers.getMapper(ChatSessionMapper.class), roomsApi);
    }

    @Test
    void requireOwnedSessionReturnsIdForOwner() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        when(repository.findById(sessionId)).thenReturn(Optional.of(session(sessionId, userId)));

        UUID result = service.requireOwnedSession(new CurrentUser(userId), sessionId);

        assertThat(result).isEqualTo(sessionId);
    }

    @Test
    void requireOwnedSessionRejectsAnotherUsersSession() {
        UUID ownerId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        when(repository.findById(sessionId)).thenReturn(Optional.of(session(sessionId, ownerId)));

        assertThatThrownBy(() -> service.requireOwnedSession(new CurrentUser(otherId), sessionId))
                .isInstanceOfSatisfying(ChatSessionException.class, ex ->
                        assertThat(ex.getReason()).isEqualTo(ChatSessionException.Reason.SESSION_NOT_FOUND));
    }

    @Test
    void requireOwnedSessionRejectsMissingSession() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        when(repository.findById(sessionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requireOwnedSession(new CurrentUser(userId), sessionId))
                .isInstanceOfSatisfying(ChatSessionException.class, ex ->
                        assertThat(ex.getReason()).isEqualTo(ChatSessionException.Reason.SESSION_NOT_FOUND));
    }

    @Test
    void createPersistsSessionForCurrentUserWithDefaults() {
        UUID userId = UUID.randomUUID();
        UUID personalRoomId = UUID.randomUUID();
        when(roomsApi.findPersonalForUser(userId))
                .thenReturn(Optional.of(new RoomDto(personalRoomId, userId, "Personal", true)));
        when(repository.save(any(ChatSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ChatSessionResponse response = service.create(
                new CurrentUser(userId), new CreateChatSessionRequest("  Algebra  ", null, null));

        assertThat(response.getChannel()).isEqualTo(ChatChannel.WEB);
        assertThat(response.getTitle()).isEqualTo("Algebra");

        ArgumentCaptor<ChatSession> captor = ArgumentCaptor.forClass(ChatSession.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(userId);
        assertThat(captor.getValue().getChannel()).isEqualTo(ChatChannel.WEB);
        assertThat(captor.getValue().getRoomId()).isEqualTo(personalRoomId);
        verify(roomsApi, never()).requireMember(any(CurrentUser.class), any(UUID.class));
    }

    @Test
    void createStampsRequestedRoomAfterMembershipCheck() {
        UUID userId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        CurrentUser currentUser = new CurrentUser(userId);
        when(roomsApi.requireMember(currentUser, roomId))
                .thenReturn(new RoomDto(roomId, UUID.randomUUID(), "Shared", false));
        when(repository.save(any(ChatSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.create(currentUser, new CreateChatSessionRequest(null, ChatChannel.WEB, roomId));

        ArgumentCaptor<ChatSession> captor = ArgumentCaptor.forClass(ChatSession.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getRoomId()).isEqualTo(roomId);
        verify(roomsApi).requireMember(currentUser, roomId);
        verify(roomsApi, never()).findPersonalForUser(any(UUID.class));
    }

    @Test
    void createRejectsRoomWhenCallerIsNotMember() {
        UUID userId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        CurrentUser currentUser = new CurrentUser(userId);
        when(roomsApi.requireMember(currentUser, roomId)).thenThrow(new IllegalStateException("not a member"));

        assertThatThrownBy(() ->
                service.create(currentUser, new CreateChatSessionRequest(null, ChatChannel.WEB, roomId)))
                .isInstanceOf(IllegalStateException.class);
        verify(repository, never()).save(any(ChatSession.class));
    }

    @Test
    void getAllQueriesOnlyCurrentUsersSessions() {
        UUID userId = UUID.randomUUID();
        when(repository.findAllByUserIdOrderByCreatedAtDesc(userId))
                .thenReturn(List.of(session(UUID.randomUUID(), userId)));

        List<ChatSessionResponse> result = service.getAll(new CurrentUser(userId));

        assertThat(result).hasSize(1);
        verify(repository).findAllByUserIdOrderByCreatedAtDesc(userId);
        verify(repository, never()).findAll();
    }

    @Test
    void getOrCreateCanonicalReturnsLatestActiveSession() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        when(repository.findFirstByUserIdAndChannelAndActiveTrueOrderByCreatedAtDesc(userId, ChatChannel.TELEGRAM))
                .thenReturn(Optional.of(session(sessionId, userId)));

        UUID result = service.getOrCreateCanonical(new CurrentUser(userId), ChatChannel.TELEGRAM);

        assertThat(result).isEqualTo(sessionId);
        verify(repository, never()).save(any(ChatSession.class));
    }

    @Test
    void getOrCreateCanonicalCreatesActiveSessionWhenNoneActive() {
        UUID userId = UUID.randomUUID();
        when(repository.findFirstByUserIdAndChannelAndActiveTrueOrderByCreatedAtDesc(userId, ChatChannel.TELEGRAM))
                .thenReturn(Optional.empty());
        when(repository.save(any(ChatSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.getOrCreateCanonical(new CurrentUser(userId), ChatChannel.TELEGRAM);

        ArgumentCaptor<ChatSession> captor = ArgumentCaptor.forClass(ChatSession.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getChannel()).isEqualTo(ChatChannel.TELEGRAM);
        assertThat(captor.getValue().isActive()).isTrue();
    }

    @Test
    void startNewSessionArchivesActiveAndCreatesFreshOne() {
        UUID userId = UUID.randomUUID();
        ChatSession existing = session(UUID.randomUUID(), userId);
        existing.setActive(true);
        when(repository.findAllByUserIdAndChannelAndActiveTrue(userId, ChatChannel.TELEGRAM))
                .thenReturn(List.of(existing));
        when(repository.save(any(ChatSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ChatSessionSummary summary = service.startNewSession(new CurrentUser(userId), ChatChannel.TELEGRAM);

        assertThat(existing.isActive()).isFalse();
        assertThat(summary.isActive()).isTrue();
        verify(repository).saveAll(List.of(existing));
    }

    @Test
    void activateSessionDeactivatesOthersAndActivatesTarget() {
        UUID userId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        ChatSession target = session(targetId, userId);
        target.setChannel(ChatChannel.TELEGRAM);
        target.setActive(false);
        ChatSession other = session(UUID.randomUUID(), userId);
        other.setChannel(ChatChannel.TELEGRAM);
        other.setActive(true);
        when(repository.findById(targetId)).thenReturn(Optional.of(target));
        when(repository.findAllByUserIdAndChannelAndActiveTrue(userId, ChatChannel.TELEGRAM))
                .thenReturn(List.of(other));

        service.activateSession(new CurrentUser(userId), targetId);

        assertThat(other.isActive()).isFalse();
        assertThat(target.isActive()).isTrue();
        verify(repository).save(target);
    }

    @Test
    void activateSessionRejectsAnotherUsersSession() {
        UUID ownerId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        when(repository.findById(sessionId)).thenReturn(Optional.of(session(sessionId, ownerId)));

        assertThatThrownBy(() -> service.activateSession(new CurrentUser(otherId), sessionId))
                .isInstanceOf(ChatSessionException.class);
        verify(repository, never()).save(any(ChatSession.class));
    }

    @Test
    void forgetActiveSessionDeletesSummaryForCanonicalSession() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        when(repository.findFirstByUserIdAndChannelAndActiveTrueOrderByCreatedAtDesc(userId, ChatChannel.TELEGRAM))
                .thenReturn(Optional.of(session(sessionId, userId)));
        ChatMemorySummary summary = new ChatMemorySummary();
        when(memorySummaries.findBySessionId(sessionId)).thenReturn(Optional.of(summary));

        service.forgetActiveSession(new CurrentUser(userId), ChatChannel.TELEGRAM);

        verify(memorySummaries).delete(summary);
    }

    private static ChatSession session(UUID id, UUID userId) {
        ChatSession session = new ChatSession();
        session.setId(id);
        session.setUserId(userId);
        session.setChannel(ChatChannel.WEB);
        return session;
    }
}
