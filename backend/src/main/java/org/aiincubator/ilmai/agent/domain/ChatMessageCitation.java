package org.aiincubator.ilmai.agent.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageCitation {

    private String id;
    private UUID materialId;
    private String materialName;
    private String locator;
    private String snippet;
    private Double score;
}
