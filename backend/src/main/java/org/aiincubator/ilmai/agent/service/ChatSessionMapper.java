package org.aiincubator.ilmai.agent.service;

import org.aiincubator.ilmai.agent.api.ChatSessionResponse;
import org.aiincubator.ilmai.agent.domain.ChatSession;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ChatSessionMapper {

    ChatSessionResponse toResponse(ChatSession session);
}
