package org.aiincubator.ilmai.ai.ingestion.reader;

import org.aiincubator.ilmai.materials.MaterialDto;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public interface MaterialReader {

    boolean supports(String contentType);

    List<MaterialPart> read(InputStream blob, MaterialDto material) throws IOException;
}
