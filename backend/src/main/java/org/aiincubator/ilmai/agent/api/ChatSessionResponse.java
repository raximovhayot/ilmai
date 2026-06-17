package org.aiincubator.ilmai.agent.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.aiincubator.ilmai.agent.ChatChannel;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatSessionResponse {

    private UUID id;
    private ChatChannel channel;
    private String title;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
