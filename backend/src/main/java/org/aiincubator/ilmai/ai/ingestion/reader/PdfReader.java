package org.aiincubator.ilmai.ai.ingestion.reader;

import lombok.RequiredArgsConstructor;
import org.aiincubator.ilmai.ai.ingestion.IngestionProperties;
import org.aiincubator.ilmai.materials.MaterialDto;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class PdfReader implements MaterialReader {

    private static final String PDF = "application/pdf";

    private final IngestionProperties properties;

    @Override
    public boolean supports(String contentType) {
        return PDF.equals(normalize(contentType));
    }

    @Override
    public List<MaterialPart> read(InputStream blob, MaterialDto material) throws IOException {
        int pagesPerChunk = Math.max(1, properties.getPdf().getPagesPerChunk());
        int overlap = Math.min(Math.max(0, properties.getPdf().getPageOverlap()), pagesPerChunk - 1);
        int step = pagesPerChunk - overlap;
        try (PDDocument source = Loader.loadPDF(blob.readAllBytes())) {
            int total = source.getNumberOfPages();
            List<MaterialPart> parts = new ArrayList<>();
            for (int start = 0; start < total; start += step) {
                int endExclusive = Math.min(start + pagesPerChunk, total);
                try (PDDocument slice = new PDDocument()) {
                    for (int i = start; i < endExclusive; i++) {
                        slice.addPage(source.getPage(i));
                    }
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    slice.save(baos);
                    parts.add(new PdfRangePart(start + 1, endExclusive, baos.toByteArray()));
                }
                if (endExclusive == total) {
                    break;
                }
            }
            return parts;
        }
    }

    private String normalize(String contentType) {
        if (contentType == null) {
            return "";
        }
        int semicolon = contentType.indexOf(';');
        String bare = semicolon >= 0 ? contentType.substring(0, semicolon) : contentType;
        return bare.trim().toLowerCase();
    }
}
