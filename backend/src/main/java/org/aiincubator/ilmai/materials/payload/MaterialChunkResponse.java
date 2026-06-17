package org.aiincubator.ilmai.materials.payload;

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
public class MaterialChunkResponse {

    private UUID materialId;
    private String materialName;
    private Integer chunkIndex;
    private String content;
    private Double score;
}
