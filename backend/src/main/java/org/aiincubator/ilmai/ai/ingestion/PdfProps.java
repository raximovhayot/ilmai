package org.aiincubator.ilmai.ai.ingestion;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PdfProps {

    private int pagesPerChunk = 6;
    private int pageOverlap = 2;
}
