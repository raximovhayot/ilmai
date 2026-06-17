package org.aiincubator.ilmai.ai.ingestion.reader;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Getter
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@ToString(of = {"startMs", "endMs", "mimeType"})
public final class AudioSegmentPart extends MaterialPart {

    private final long startMs;
    private final long endMs;
    private final String mimeType;
    private final byte[] segmentBytes;
}
