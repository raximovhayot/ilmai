package org.aiincubator.ilmai.ai.embedding;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.List;

@Getter
@AllArgsConstructor
@EqualsAndHashCode
@ToString
public final class MultimodalContent {

    private final List<MultimodalPart> parts;
    private final String taskType;

    public MultimodalContent(List<MultimodalPart> parts) {
        this(parts, null);
    }
}
