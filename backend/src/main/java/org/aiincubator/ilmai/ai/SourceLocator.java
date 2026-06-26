package org.aiincubator.ilmai.ai;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SourceLocator {

    private final String kind;
    private final Integer pageStart;
    private final Integer pageEnd;
    private final Long audioStartMs;
    private final Long audioEndMs;
}
