package org.aiincubator.ilmai.agent.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.aiincubator.ilmai.agent.ChatChannel;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AgentChatRequest {

    @NotBlank
    @Size(max = 8000)
    private String prompt;

    @Size(max = 24000)
    private String context;

    private ChatChannel channel;
}
