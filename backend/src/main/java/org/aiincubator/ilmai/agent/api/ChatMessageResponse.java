package org.aiincubator.ilmai.agent.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.aiincubator.ilmai.agent.ChatMessageRole;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessageResponse {

    private UUID id;
    private ChatMessageRole role;
    private String content;
    private List<ChatMessageCitationResponse> citations;
    private boolean lowConfidence;
    private OffsetDateTime createdAt;
}
