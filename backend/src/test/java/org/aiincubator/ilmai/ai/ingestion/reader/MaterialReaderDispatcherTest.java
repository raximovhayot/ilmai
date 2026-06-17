package org.aiincubator.ilmai.ai.ingestion.reader;

import org.aiincubator.ilmai.ai.ingestion.IngestionProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MaterialReaderDispatcherTest {

    @Test
    void dispatch_returnsPlainTextReaderForTextPlain() {
        PlainTextReader plain = new PlainTextReader();
        TikaReader tika = new TikaReader();
        MaterialReaderDispatcher dispatcher = new MaterialReaderDispatcher(List.of(plain, tika));

        MaterialReader resolved = dispatcher.dispatch("text/plain");

        assertThat(resolved).isSameAs(plain);
    }

    @Test
    void dispatch_returnsPlainTextReaderForMarkdownIgnoringCharset() {
        PlainTextReader plain = new PlainTextReader();
        TikaReader tika = new TikaReader();
        MaterialReaderDispatcher dispatcher = new MaterialReaderDispatcher(List.of(plain, tika));

        MaterialReader resolved = dispatcher.dispatch("text/markdown; charset=utf-8");

        assertThat(resolved).isSameAs(plain);
    }

    @Test
    void dispatch_returnsPdfReaderForPdf() {
        PlainTextReader plain = new PlainTextReader();
        TikaReader tika = new TikaReader();
        PdfReader pdf = new PdfReader(new IngestionProperties());
        MaterialReaderDispatcher dispatcher = new MaterialReaderDispatcher(List.of(plain, tika, pdf));

        MaterialReader resolved = dispatcher.dispatch("application/pdf");

        assertThat(resolved).isSameAs(pdf);
    }

    @Test
    void dispatch_returnsAudioReaderForAudioMpeg() {
        PlainTextReader plain = new PlainTextReader();
        AudioReader audio = new AudioReader(new IngestionProperties());
        MaterialReaderDispatcher dispatcher = new MaterialReaderDispatcher(List.of(plain, audio));

        MaterialReader resolved = dispatcher.dispatch("audio/mpeg");

        assertThat(resolved).isSameAs(audio);
    }

    @Test
    void dispatch_throwsForUnsupportedAudioType() {
        AudioReader audio = new AudioReader(new IngestionProperties());
        MaterialReaderDispatcher dispatcher = new MaterialReaderDispatcher(List.of(audio));

        assertThatThrownBy(() -> dispatcher.dispatch("audio/ogg"))
                .isInstanceOf(UnsupportedMaterialFormatException.class);
    }

    @Test
    void dispatch_returnsTikaReaderForDocx() {
        PlainTextReader plain = new PlainTextReader();
        TikaReader tika = new TikaReader();
        MaterialReaderDispatcher dispatcher = new MaterialReaderDispatcher(List.of(plain, tika));

        MaterialReader resolved = dispatcher.dispatch(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

        assertThat(resolved).isSameAs(tika);
    }

    @Test
    void dispatch_returnsTikaReaderForPptx() {
        PlainTextReader plain = new PlainTextReader();
        TikaReader tika = new TikaReader();
        MaterialReaderDispatcher dispatcher = new MaterialReaderDispatcher(List.of(plain, tika));

        MaterialReader resolved = dispatcher.dispatch(
                "application/vnd.openxmlformats-officedocument.presentationml.presentation");

        assertThat(resolved).isSameAs(tika);
    }

    @Test
    void dispatch_throwsForUnknownContentType() {
        PlainTextReader plain = new PlainTextReader();
        TikaReader tika = new TikaReader();
        MaterialReaderDispatcher dispatcher = new MaterialReaderDispatcher(List.of(plain, tika));

        assertThatThrownBy(() -> dispatcher.dispatch("image/png"))
                .isInstanceOf(UnsupportedMaterialFormatException.class)
                .hasMessageContaining("image/png");
    }

    @Test
    void dispatch_throwsForNullContentType() {
        PlainTextReader plain = new PlainTextReader();
        MaterialReaderDispatcher dispatcher = new MaterialReaderDispatcher(List.of(plain));

        assertThatThrownBy(() -> dispatcher.dispatch(null))
                .isInstanceOf(UnsupportedMaterialFormatException.class);
    }
}
