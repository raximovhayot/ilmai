package org.aiincubator.ilmai.agent.service;

import org.aiincubator.ilmai.agent.api.ChatMessageCitationResponse;
import org.aiincubator.ilmai.agent.api.ChatMessageResponse;
import org.aiincubator.ilmai.agent.domain.ChatMessage;
import org.aiincubator.ilmai.agent.domain.ChatMessageCitation;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ChatMessageMapper {

    ChatMessageResponse toResponse(ChatMessage message);

    List<ChatMessageResponse> toResponses(List<ChatMessage> messages);

    ChatMessageCitationResponse toCitationResponse(ChatMessageCitation citation);
}
