package org.aiincubator.ilmai.ai.ingestion.reader;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Getter
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@ToString(of = {"mimeType"})
public final class ImagePart extends MaterialPart {

    private final String mimeType;
    private final byte[] data;
}
