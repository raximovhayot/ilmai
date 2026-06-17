package org.aiincubator.ilmai.ai.ingestion.reader;

import org.aiincubator.ilmai.materials.MaterialDto;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TikaReaderTest {

    @Test
    void supports_acceptsConfiguredOfficeTypes() {
        TikaReader reader = new TikaReader();

        assertThat(reader.supports("application/msword")).isTrue();
        assertThat(reader.supports("application/vnd.openxmlformats-officedocument.wordprocessingml.document")).isTrue();
        assertThat(reader.supports("application/vnd.ms-powerpoint")).isTrue();
        assertThat(reader.supports("application/vnd.openxmlformats-officedocument.presentationml.presentation")).isTrue();
    }

    @Test
    void supports_rejectsPdfAndPlainText() {
        TikaReader reader = new TikaReader();

        assertThat(reader.supports("application/pdf")).isFalse();
        assertThat(reader.supports("text/plain")).isFalse();
        assertThat(reader.supports("text/markdown")).isFalse();
        assertThat(reader.supports(null)).isFalse();
    }

    @Test
    void read_extractsTextFromDocxFixtureAsTextParts() throws IOException {
        TikaReader reader = new TikaReader();
        byte[] docxBytes = generateDocxFixture("Hello DOCX fixture for Tika");

        List<MaterialPart> parts = reader.read(new ByteArrayInputStream(docxBytes), new MaterialDto(java.util.UUID.randomUUID(), java.util.UUID.randomUUID(), java.util.UUID.randomUUID(), "test", null, 0L, null, 0, null, null));

        assertThat(parts).isNotEmpty();
        assertThat(parts).allSatisfy(p -> assertThat(p).isInstanceOf(TextMaterialPart.class));
        TextMaterialPart first = (TextMaterialPart) parts.getFirst();
        assertThat(first.getText()).contains("Hello DOCX fixture for Tika");
    }

    private byte[] generateDocxFixture(String text) throws IOException {
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XWPFParagraph paragraph = document.createParagraph();
            XWPFRun run = paragraph.createRun();
            run.setText(text);
            document.write(out);
            return out.toByteArray();
        }
    }
}
