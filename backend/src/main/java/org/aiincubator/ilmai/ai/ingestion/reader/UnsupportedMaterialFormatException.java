package org.aiincubator.ilmai.ai.ingestion.reader;

public class UnsupportedMaterialFormatException extends RuntimeException {

    public UnsupportedMaterialFormatException(String contentType) {
        super("No MaterialReader supports content type: " + contentType);
    }
}
