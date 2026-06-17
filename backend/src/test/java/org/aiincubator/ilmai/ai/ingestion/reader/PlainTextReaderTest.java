package org.aiincubator.ilmai.ai.ingestion.reader;

import org.aiincubator.ilmai.materials.MaterialDto;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlainTextReaderTest {

    @Test
    void supports_returnsTrueForTextPlain() {
        PlainTextReader reader = new PlainTextReader();

        assertThat(reader.supports("text/plain")).isTrue();
        assertThat(reader.supports("text/markdown")).isTrue();
        assertThat(reader.supports("TEXT/PLAIN")).isTrue();
        assertThat(reader.supports("text/plain; charset=utf-8")).isTrue();
    }

    @Test
    void supports_returnsFalseForBinaryTypes() {
        PlainTextReader reader = new PlainTextReader();

        assertThat(reader.supports("application/pdf")).isFalse();
        assertThat(reader.supports("application/msword")).isFalse();
        assertThat(reader.supports(null)).isFalse();
    }

    @Test
    void read_decodesUtf8BytesIntoSingleTextPart() throws IOException {
        PlainTextReader reader = new PlainTextReader();
        String text = "# Hello world\n\nThis is a markdown sample with cyrillic: тест.";
        ByteArrayInputStream blob = new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));

        List<MaterialPart> parts = reader.read(blob, new MaterialDto(java.util.UUID.randomUUID(), java.util.UUID.randomUUID(), java.util.UUID.randomUUID(), "test", null, 0L, null, 0, null, null));

        assertThat(parts).hasSize(1);
        assertThat(parts.getFirst()).isInstanceOfSatisfying(TextMaterialPart.class, p ->
                assertThat(p.getText()).isEqualTo(text));
    }
}
