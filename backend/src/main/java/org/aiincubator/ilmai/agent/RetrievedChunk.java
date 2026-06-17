package org.aiincubator.ilmai.agent;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor
public class RetrievedChunk {

    private final UUID materialId;
    private final String materialName;
    private final Integer chunkIndex;
    private final String snippet;
    private final Double score;
}
