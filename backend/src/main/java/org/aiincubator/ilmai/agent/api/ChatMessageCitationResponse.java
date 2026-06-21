package org.aiincubator.ilmai.agent.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessageCitationResponse {

    private String id;
    private UUID materialId;
    private String materialName;
    private String locator;
    private String snippet;
    private Double score;
}
