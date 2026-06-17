package org.aiincubator.ilmai.agent.api;

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
public class CreateChatSessionRequest {

    @Size(max = 200)
    private String title;

    private ChatChannel channel;
}
