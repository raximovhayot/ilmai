package org.aiincubator.ilmai.agent.service;

import lombok.RequiredArgsConstructor;
import org.aiincubator.ilmai.agent.ChatChannel;
import org.aiincubator.ilmai.agent.ChatSessionSummary;
import org.aiincubator.ilmai.agent.api.ChatSessionResponse;
import org.aiincubator.ilmai.agent.api.CreateChatSessionRequest;
import org.aiincubator.ilmai.agent.domain.ChatMemorySummaryRepository;
import org.aiincubator.ilmai.agent.domain.ChatSession;
import org.aiincubator.ilmai.agent.domain.ChatSessionRepository;
import org.aiincubator.ilmai.common.CurrentUser;
import org.aiincubator.ilmai.rooms.RoomDto;
import org.aiincubator.ilmai.rooms.RoomsApi;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatSessionService {

    private static final int TITLE_MAX_LENGTH = 200;
    private static final int RECENT_SESSIONS_LIMIT = 8;

    private final ChatSessionRepository sessions;
    private final ChatMemorySummaryRepository memorySummaries;
    private final ChatSessionMapper chatSessionMapper;
    private final RoomsApi roomsApi;

    @Transactional
    public ChatSessionResponse create(CurrentUser currentUser, CreateChatSessionRequest request) {
        ChatSession session = new ChatSession();
        session.setUserId(currentUser.getUserId());
        session.setRoomId(personalRoomId(currentUser.getUserId()));
        session.setChannel(resolveChannel(request));
        session.setTitle(normalizeTitle(request));
        return chatSessionMapper.toResponse(sessions.save(session));
    }

    @Transactional(readOnly = true)
    public List<ChatSessionResponse> getAll(CurrentUser currentUser) {
        return sessions.findAllByUserIdOrderByCreatedAtDesc(currentUser.getUserId()).stream()
                .map(chatSessionMapper::toResponse)
                .toList();
    }

    @Transactional
    public UUID getOrCreateCanonical(CurrentUser currentUser, ChatChannel channel) {
        ChatChannel effective = channel == null ? ChatChannel.WEB : channel;
        return sessions.findFirstByUserIdAndChannelAndActiveTrueOrderByCreatedAtDesc(
                        currentUser.getUserId(), effective)
                .map(ChatSession::getId)
                .orElseGet(() -> createSession(currentUser, effective).getId());
    }

    @Transactional
    public ChatSessionSummary startNewSession(CurrentUser currentUser, ChatChannel channel) {
        ChatChannel effective = channel == null ? ChatChannel.WEB : channel;
        archiveActive(currentUser.getUserId(), effective);
        return chatSessionMapper.toSummary(createSession(currentUser, effective));
    }

    @Transactional(readOnly = true)
    public List<ChatSessionSummary> recentSessions(CurrentUser currentUser, ChatChannel channel) {
        ChatChannel effective = channel == null ? ChatChannel.WEB : channel;
        return sessions.findAllByUserIdAndChannelOrderByCreatedAtDesc(currentUser.getUserId(), effective).stream()
                .limit(RECENT_SESSIONS_LIMIT)
                .map(chatSessionMapper::toSummary)
                .toList();
    }

    @Transactional
    public void activateSession(CurrentUser currentUser, UUID sessionId) {
        ChatSession session = ownedSession(currentUser, sessionId);
        archiveActive(currentUser.getUserId(), session.getChannel());
        session.setActive(true);
        sessions.save(session);
    }

    @Transactional
    public void forgetActiveSession(CurrentUser currentUser, ChatChannel channel) {
        UUID sessionId = getOrCreateCanonical(currentUser, channel);
        memorySummaries.findBySessionId(sessionId).ifPresent(memorySummaries::delete);
    }

    private ChatSession createSession(CurrentUser currentUser, ChatChannel channel) {
        ChatSession session = new ChatSession();
        session.setUserId(currentUser.getUserId());
        session.setRoomId(personalRoomId(currentUser.getUserId()));
        session.setChannel(channel);
        session.setActive(true);
        return sessions.save(session);
    }

    private UUID personalRoomId(UUID userId) {
        return roomsApi.findPersonalForUser(userId)
                .map(RoomDto::getId)
                .orElseThrow(() -> new ChatSessionException(ChatSessionException.Reason.SESSION_NOT_FOUND));
    }

    private void archiveActive(UUID userId, ChatChannel channel) {
        List<ChatSession> active = sessions.findAllByUserIdAndChannelAndActiveTrue(userId, channel);
        for (ChatSession session : active) {
            session.setActive(false);
        }
        sessions.saveAll(active);
    }

    @Transactional(readOnly = true)
    public UUID requireOwnedSession(CurrentUser currentUser, UUID sessionId) {
        return ownedSession(currentUser, sessionId).getId();
    }

    @Transactional(readOnly = true)
    public UUID requireOwnedSessionRoomId(CurrentUser currentUser, UUID sessionId) {
        return ownedSession(currentUser, sessionId).getRoomId();
    }

    private ChatSession ownedSession(CurrentUser currentUser, UUID sessionId) {
        ChatSession session = sessions.findById(sessionId)
                .orElseThrow(() -> new ChatSessionException(ChatSessionException.Reason.SESSION_NOT_FOUND));
        if (!currentUser.getUserId().equals(session.getUserId())) {
            throw new ChatSessionException(ChatSessionException.Reason.SESSION_NOT_FOUND);
        }
        return session;
    }

    private ChatChannel resolveChannel(CreateChatSessionRequest request) {
        if (request == null || request.getChannel() == null) {
            return ChatChannel.WEB;
        }
        return request.getChannel();
    }

    private String normalizeTitle(CreateChatSessionRequest request) {
        if (request == null || request.getTitle() == null) {
            return null;
        }
        String trimmed = request.getTitle().trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() > TITLE_MAX_LENGTH ? trimmed.substring(0, TITLE_MAX_LENGTH) : trimmed;
    }
}
