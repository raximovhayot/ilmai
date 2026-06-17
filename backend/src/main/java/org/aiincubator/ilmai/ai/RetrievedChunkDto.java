package org.aiincubator.ilmai.ai;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor
public class RetrievedChunkDto {

    private final UUID materialId;
    private final String materialName;
    private final Integer chunkIndex;
    private final String content;
    private final Double score;
}
