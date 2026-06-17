package org.aiincubator.ilmai.ai.ingestion.reader;

import org.aiincubator.ilmai.ai.ingestion.IngestionProperties;
import org.aiincubator.ilmai.materials.MaterialDto;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PdfReaderTest {

    @Test
    void supports_acceptsOnlyPdf() {
        PdfReader reader = new PdfReader(new IngestionProperties());

        assertThat(reader.supports("application/pdf")).isTrue();
        assertThat(reader.supports("APPLICATION/PDF")).isTrue();
        assertThat(reader.supports("application/pdf; charset=utf-8")).isTrue();
        assertThat(reader.supports("text/plain")).isFalse();
        assertThat(reader.supports("application/msword")).isFalse();
        assertThat(reader.supports(null)).isFalse();
    }

    @Test
    void read_slicesIntoOverlappingRanges() throws IOException {
        PdfReader reader = new PdfReader(new IngestionProperties());
        byte[] pdfBytes = generatePdf(13);

        List<MaterialPart> parts = reader.read(new ByteArrayInputStream(pdfBytes), material());

        assertThat(parts).hasSize(3);
        assertThat(parts).allSatisfy(p -> assertThat(p).isInstanceOf(PdfRangePart.class));

        PdfRangePart first = (PdfRangePart) parts.get(0);
        assertThat(first.getPageStart()).isEqualTo(1);
        assertThat(first.getPageEnd()).isEqualTo(6);
        assertThat(pageCount(first.getPdfBytes())).isEqualTo(6);

        PdfRangePart second = (PdfRangePart) parts.get(1);
        assertThat(second.getPageStart()).isEqualTo(5);
        assertThat(second.getPageEnd()).isEqualTo(10);
        assertThat(pageCount(second.getPdfBytes())).isEqualTo(6);

        PdfRangePart third = (PdfRangePart) parts.get(2);
        assertThat(third.getPageStart()).isEqualTo(9);
        assertThat(third.getPageEnd()).isEqualTo(13);
        assertThat(pageCount(third.getPdfBytes())).isEqualTo(5);
    }

    @Test
    void read_withoutOverlapTilesContiguously() throws IOException {
        IngestionProperties properties = new IngestionProperties();
        properties.getPdf().setPageOverlap(0);
        PdfReader reader = new PdfReader(properties);
        byte[] pdfBytes = generatePdf(13);

        List<MaterialPart> parts = reader.read(new ByteArrayInputStream(pdfBytes), material());

        assertThat(parts).hasSize(3);
        assertThat(((PdfRangePart) parts.get(0)).getPageStart()).isEqualTo(1);
        assertThat(((PdfRangePart) parts.get(0)).getPageEnd()).isEqualTo(6);
        assertThat(((PdfRangePart) parts.get(1)).getPageStart()).isEqualTo(7);
        assertThat(((PdfRangePart) parts.get(1)).getPageEnd()).isEqualTo(12);
        assertThat(((PdfRangePart) parts.get(2)).getPageStart()).isEqualTo(13);
        assertThat(((PdfRangePart) parts.get(2)).getPageEnd()).isEqualTo(13);
        assertThat(pageCount(((PdfRangePart) parts.get(2)).getPdfBytes())).isEqualTo(1);
    }

    @Test
    void read_singleChunkWhenFewerPagesThanWindow() throws IOException {
        PdfReader reader = new PdfReader(new IngestionProperties());
        byte[] pdfBytes = generatePdf(3);

        List<MaterialPart> parts = reader.read(new ByteArrayInputStream(pdfBytes), material());

        assertThat(parts).hasSize(1);
        PdfRangePart only = (PdfRangePart) parts.get(0);
        assertThat(only.getPageStart()).isEqualTo(1);
        assertThat(only.getPageEnd()).isEqualTo(3);
        assertThat(pageCount(only.getPdfBytes())).isEqualTo(3);
    }

    private MaterialDto material() {
        return new MaterialDto(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "test", null, 0L, null, 0, null, null);
    }

    private int pageCount(byte[] pdfBytes) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            return document.getNumberOfPages();
        }
    }

    private byte[] generatePdf(int pageCount) throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (int i = 1; i <= pageCount; i++) {
                PDPage page = new PDPage();
                document.addPage(page);
                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    content.beginText();
                    content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 14f);
                    content.newLineAtOffset(72, 720);
                    content.showText("Page " + i + " content");
                    content.endText();
                }
            }
            document.save(out);
            return out.toByteArray();
        }
    }
}
