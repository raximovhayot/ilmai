package org.aiincubator.ilmai.ai.ingestion.reader;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Getter
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@ToString(of = {"pageStart", "pageEnd"})
public final class PdfRangePart extends MaterialPart {

    private final int pageStart;
    private final int pageEnd;
    private final byte[] pdfBytes;
}
