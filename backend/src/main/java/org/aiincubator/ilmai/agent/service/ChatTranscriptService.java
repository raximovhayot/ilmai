package org.aiincubator.ilmai.agent.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aiincubator.ilmai.agent.ChatMessageRole;
import org.aiincubator.ilmai.agent.RetrievedChunk;
import org.aiincubator.ilmai.agent.api.ChatMessageResponse;
import org.aiincubator.ilmai.agent.domain.ChatMessage;
import org.aiincubator.ilmai.agent.domain.ChatMessageCitation;
import org.aiincubator.ilmai.agent.domain.ChatMessageRepository;
import org.aiincubator.ilmai.common.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class ChatTranscriptService {

    private final ChatMessageRepository messages;
    private final ChatMessageMapper chatMessageMapper;
    private final ChatSessionService chatSessionService;

    @Transactional(readOnly = true)
    public List<ChatMessageResponse> getMessages(CurrentUser currentUser, UUID sessionId) {
        UUID ownedSessionId = chatSessionService.requireOwnedSession(currentUser, sessionId);
        return chatMessageMapper.toResponses(
                messages.findAllBySessionIdOrderByCreatedAtAscIdAsc(ownedSessionId));
    }

    @Transactional
    public void recordUserTurn(CurrentUser currentUser, UUID sessionId, String content) {
        if (content == null || content.isBlank()) {
            return;
        }
        ChatMessage message = new ChatMessage();
        message.setSessionId(sessionId);
        message.setUserId(currentUser.getUserId());
        message.setRole(ChatMessageRole.USER);
        message.setContent(content);
        messages.save(message);
    }

    @Transactional
    public void recordAssistantTurn(CurrentUser currentUser, UUID sessionId, String content,
                                    List<RetrievedChunk> chunks, boolean lowConfidence) {
        if (content == null || content.isBlank()) {
            return;
        }
        ChatMessage message = new ChatMessage();
        message.setSessionId(sessionId);
        message.setUserId(currentUser.getUserId());
        message.setRole(ChatMessageRole.ASSISTANT);
        message.setContent(content);
        message.setCitations(toCitations(chunks));
        message.setLowConfidence(lowConfidence);
        messages.save(message);
    }

    private List<ChatMessageCitation> toCitations(List<RetrievedChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return null;
        }
        List<ChatMessageCitation> citations = new ArrayList<>(chunks.size());
        for (RetrievedChunk chunk : chunks) {
            citations.add(new ChatMessageCitation(
                    UUID.randomUUID().toString(),
                    chunk.getMaterialId(),
                    chunk.getMaterialName(),
                    chunk.getChunkIndex() == null ? null : "t" + chunk.getChunkIndex(),
                    chunk.getSnippet(),
                    chunk.getScore() == null ? 0.0 : chunk.getScore()));
        }
        return citations;
    }
}
