package org.aiincubator.ilmai.agent;

import org.aiincubator.ilmai.common.CurrentUser;
import reactor.core.publisher.Flux;

import java.util.UUID;

public interface AgentApi {

    Flux<MessagePart> chat(CurrentUser currentUser, UUID sessionId, String prompt, ChatChannel channel);

    UUID canonicalSession(CurrentUser currentUser, ChatChannel channel);
}
