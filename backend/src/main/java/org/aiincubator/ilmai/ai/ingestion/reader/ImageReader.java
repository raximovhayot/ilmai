package org.aiincubator.ilmai.ai.ingestion.reader;

import org.aiincubator.ilmai.materials.MaterialDto;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Set;

@Component
public class ImageReader implements MaterialReader {

    private static final Set<String> SUPPORTED = Set.of("image/png", "image/jpeg");

    @Override
    public boolean supports(String contentType) {
        return SUPPORTED.contains(normalize(contentType));
    }

    @Override
    public List<MaterialPart> read(InputStream blob, MaterialDto material) throws IOException {
        byte[] bytes = blob.readAllBytes();
        String mimeType = normalize(material == null ? null : material.getContentType());
        if (!SUPPORTED.contains(mimeType)) {
            mimeType = "image/png";
        }
        return List.of(new ImagePart(mimeType, bytes));
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
