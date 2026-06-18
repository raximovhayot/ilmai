package org.aiincubator.ilmai.agent;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.UUID;

@Getter
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@ToString
public class CitationPart extends MessagePart {

    private final UUID id;
    private final UUID materialId;
    private final String materialName;
    private final String locator;
    private final String snippet;
    private final double score;
}
