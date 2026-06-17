package org.aiincubator.ilmai.ai.ingestion.reader;

import org.aiincubator.ilmai.materials.MaterialDto;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.core.io.InputStreamResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
public class TikaReader implements MaterialReader {

    private static final Set<String> SUPPORTED = Set.of(
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation"
    );

    @Override
    public boolean supports(String contentType) {
        return SUPPORTED.contains(normalize(contentType));
    }

    @Override
    public List<MaterialPart> read(InputStream blob, MaterialDto material) {
        TikaDocumentReader reader = new TikaDocumentReader(new InputStreamResource(blob));
        List<Document> documents = reader.get();
        List<MaterialPart> parts = new ArrayList<>(documents.size());
        for (Document doc : documents) {
            String text = doc.getText();
            if (text != null && !text.isEmpty()) {
                parts.add(new TextMaterialPart(text));
            }
        }
        return parts;
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
