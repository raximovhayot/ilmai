package org.aiincubator.ilmai.ai.ingestion.reader;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MaterialReaderDispatcher {

    private final List<MaterialReader> readers;

    public MaterialReaderDispatcher(List<MaterialReader> readers) {
        this.readers = List.copyOf(readers);
    }

    public MaterialReader dispatch(String contentType) {
        for (MaterialReader reader : readers) {
            if (reader.supports(contentType)) {
                return reader;
            }
        }
        throw new UnsupportedMaterialFormatException(contentType);
    }
}
