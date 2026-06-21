package org.aiincubator.ilmai.agent;

import org.aiincubator.ilmai.common.CurrentUser;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.UUID;

public interface AgentApi {

    Flux<MessagePart> chat(CurrentUser currentUser, UUID sessionId, String prompt, ChatChannel channel);

    UUID canonicalSession(CurrentUser currentUser, ChatChannel channel);

    ChatSessionSummary startNewSession(CurrentUser currentUser, ChatChannel channel);

    List<ChatSessionSummary> recentSessions(CurrentUser currentUser, ChatChannel channel);

    void activateSession(CurrentUser currentUser, UUID sessionId);

    void forgetActiveSession(CurrentUser currentUser, ChatChannel channel);
}
