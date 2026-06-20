package org.aiincubator.ilmai.plan.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LessonCitation {

    private UUID materialId;
    private String materialName;
    private Integer chunkIndex;
    private String snippet;
}
