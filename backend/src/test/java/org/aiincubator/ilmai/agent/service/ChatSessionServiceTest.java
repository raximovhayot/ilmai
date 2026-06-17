package org.aiincubator.ilmai.agent.service;

import org.aiincubator.ilmai.agent.ChatChannel;
import org.aiincubator.ilmai.agent.api.ChatSessionResponse;
import org.aiincubator.ilmai.agent.api.CreateChatSessionRequest;
import org.aiincubator.ilmai.agent.domain.ChatSession;
import org.aiincubator.ilmai.agent.domain.ChatSessionRepository;
import org.aiincubator.ilmai.common.CurrentUser;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatSessionServiceTest {

    private ChatSessionRepository repository;
    private ChatSessionService service;

    @BeforeEach
    void setUp() {
        repository = mock(ChatSessionRepository.class);
        service = new ChatSessionService(repository, Mappers.getMapper(ChatSessionMapper.class));
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
        when(repository.save(any(ChatSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ChatSessionResponse response = service.create(
                new CurrentUser(userId), new CreateChatSessionRequest("  Algebra  ", null));

        assertThat(response.getChannel()).isEqualTo(ChatChannel.WEB);
        assertThat(response.getTitle()).isEqualTo("Algebra");

        ArgumentCaptor<ChatSession> captor = ArgumentCaptor.forClass(ChatSession.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(userId);
        assertThat(captor.getValue().getChannel()).isEqualTo(ChatChannel.WEB);
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

    private static ChatSession session(UUID id, UUID userId) {
        ChatSession session = new ChatSession();
        session.setId(id);
        session.setUserId(userId);
        session.setChannel(ChatChannel.WEB);
        return session;
    }
}
