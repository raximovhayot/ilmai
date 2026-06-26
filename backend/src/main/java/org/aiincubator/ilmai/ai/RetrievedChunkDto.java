package org.aiincubator.ilmai.ai;

import lombok.Getter;

import java.util.UUID;

@Getter
public class RetrievedChunkDto {

    private final UUID materialId;
    private final String materialName;
    private final Integer chunkIndex;
    private final String content;
    private final Double score;
    private final SourceLocator locator;

    public RetrievedChunkDto(UUID materialId, String materialName, Integer chunkIndex, String content, Double score) {
        this(materialId, materialName, chunkIndex, content, score, null);
    }

    public RetrievedChunkDto(UUID materialId, String materialName, Integer chunkIndex, String content, Double score,
                             SourceLocator locator) {
        this.materialId = materialId;
        this.materialName = materialName;
        this.chunkIndex = chunkIndex;
        this.content = content;
        this.score = score;
        this.locator = locator;
    }
}
