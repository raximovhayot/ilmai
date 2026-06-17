package org.aiincubator.ilmai.ai.ingestion;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AudioProps {

    private long windowMs = 120_000L;
    private long overlapMs = 5_000L;
}
