package org.aiincubator.ilmai.ai.embedding;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Getter
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@ToString(of = "mimeType")
public final class InlineDataPart extends MultimodalPart {

    private final String mimeType;
    private final byte[] data;
}
