package org.aiincubator.ilmai.agent.service;

import lombok.RequiredArgsConstructor;
import org.aiincubator.ilmai.agent.ChatChannel;
import org.aiincubator.ilmai.agent.api.ChatSessionResponse;
import org.aiincubator.ilmai.agent.api.CreateChatSessionRequest;
import org.aiincubator.ilmai.agent.domain.ChatSession;
import org.aiincubator.ilmai.agent.domain.ChatSessionRepository;
import org.aiincubator.ilmai.common.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatSessionService {

    private static final int TITLE_MAX_LENGTH = 200;

    private final ChatSessionRepository sessions;
    private final ChatSessionMapper chatSessionMapper;

    @Transactional
    public ChatSessionResponse create(CurrentUser currentUser, CreateChatSessionRequest request) {
        ChatSession session = new ChatSession();
        session.setUserId(currentUser.getUserId());
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
        return sessions.findFirstByUserIdAndChannelOrderByCreatedAtAsc(currentUser.getUserId(), effective)
                .map(ChatSession::getId)
                .orElseGet(() -> {
                    ChatSession session = new ChatSession();
                    session.setUserId(currentUser.getUserId());
                    session.setChannel(effective);
                    return sessions.save(session).getId();
                });
    }

    @Transactional(readOnly = true)
    public UUID requireOwnedSession(CurrentUser currentUser, UUID sessionId) {
        ChatSession session = sessions.findById(sessionId)
                .orElseThrow(() -> new ChatSessionException(ChatSessionException.Reason.SESSION_NOT_FOUND));
        if (!currentUser.getUserId().equals(session.getUserId())) {
            throw new ChatSessionException(ChatSessionException.Reason.SESSION_NOT_FOUND);
        }
        return session.getId();
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
