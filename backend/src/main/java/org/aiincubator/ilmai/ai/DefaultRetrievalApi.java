package org.aiincubator.ilmai.ai;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@EnableConfigurationProperties(RetrievalProperties.class)
@Slf4j
public class DefaultRetrievalApi implements RetrievalApi {

    private final ObjectProvider<VectorStore> vectorStoreProvider;
    private final RetrievalProperties properties;

    @Override
    public List<RetrievedChunkDto> retrieve(UUID userId, String query) {
        if (userId == null || query == null || query.isBlank()) {
            return List.of();
        }
        VectorStore store = vectorStoreProvider.getIfAvailable();
        if (store == null) {
            return List.of();
        }
        try {
            FilterExpressionBuilder b = new FilterExpressionBuilder();
            Filter.Expression filter = b.eq("user_id", userId.toString()).build();
            SearchRequest sr = SearchRequest.builder()
                    .query(query)
                    .topK(properties.getTopK())
                    .similarityThreshold(properties.getSimilarityThreshold())
                    .filterExpression(filter)
                    .build();
            List<Document> docs = store.similaritySearch(sr);
            if (docs == null || docs.isEmpty()) {
                return List.of();
            }
            List<RetrievedChunkDto> out = new ArrayList<>(docs.size());
            for (Document doc : docs) {
                Map<String, Object> md = doc.getMetadata();
                UUID materialId = parseUuid(md == null ? null : md.get("material_id"));
                String materialName = md == null ? null : stringOrNull(md.get("material_name"));
                Integer chunkIndex = parseInt(md == null ? null : md.get("chunk_index"));
                Double score = doc.getScore();
                String text = doc.getText();
                out.add(new RetrievedChunkDto(materialId, materialName, chunkIndex, text == null ? "" : text, score));
            }
            return out;
        } catch (RuntimeException ex) {
            log.warn("vector retrieval failed for user {}: {}", userId, ex.toString());
            return List.of();
        }
    }

    private static UUID parseUuid(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value.toString());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static String stringOrNull(Object value) {
        return value == null ? null : value.toString();
    }

    private static Integer parseInt(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
