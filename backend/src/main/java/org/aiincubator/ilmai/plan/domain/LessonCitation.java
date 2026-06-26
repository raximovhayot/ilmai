package org.aiincubator.ilmai.plan.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class LessonCitation {

    private UUID materialId;
    private String materialName;
    private Integer chunkIndex;
    private String snippet;
    private String sourceKind;
    private Integer pageStart;
    private Integer pageEnd;
    private Long audioStartMs;
    private Long audioEndMs;

    public LessonCitation(UUID materialId, String materialName, Integer chunkIndex, String snippet) {
        this.materialId = materialId;
        this.materialName = materialName;
        this.chunkIndex = chunkIndex;
        this.snippet = snippet;
    }
}
