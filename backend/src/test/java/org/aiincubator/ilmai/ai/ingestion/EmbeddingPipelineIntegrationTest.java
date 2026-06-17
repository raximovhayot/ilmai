package org.aiincubator.ilmai.ai.ingestion;

import org.aiincubator.ilmai.ai.ingestion.support.AbstractEmbeddingIntegrationTest;
import org.aiincubator.ilmai.ai.ingestion.support.InMemoryBlobStorage;
import org.aiincubator.ilmai.auth.domain.User;
import org.aiincubator.ilmai.auth.domain.UserRepository;
import org.aiincubator.ilmai.auth.UserStatus;
import org.aiincubator.ilmai.common.storage.BlobStorage;
import org.aiincubator.ilmai.materials.domain.Material;
import org.aiincubator.ilmai.materials.domain.MaterialRepository;
import org.aiincubator.ilmai.materials.MaterialStatus;
import org.aiincubator.ilmai.materials.MaterialStorageKeys;
import org.aiincubator.ilmai.materials.MaterialUploadedEvent;
import org.aiincubator.ilmai.spaces.domain.Space;
import org.aiincubator.ilmai.spaces.domain.SpaceRepository;
import org.aiincubator.ilmai.materials.domain.Topic;
import org.aiincubator.ilmai.materials.domain.TopicRepository;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EmbeddingPipelineIntegrationTest extends AbstractEmbeddingIntegrationTest {

    @Autowired UserRepository users;
    @Autowired SpaceRepository spaces;
    @Autowired TopicRepository topics;
    @Autowired MaterialRepository materials;
    @Autowired MaterialIngestionService ingestion;
    @Autowired BlobStorage storage;
    @Autowired VectorStore vectorStore;
    @Autowired TransactionTemplate transactionTemplate;

    @Test
    void embedAndRetrieveRoundTrip() {
        UUID materialId = transactionTemplate.execute(status -> {
            User user = persistUser("learner@example.com");
            Space space = persistSpace(user, "My Space");
            Topic topic = persistTopic(space, "Cloud Computing");
            Material material = persistMaterial(topic, "AWS notes",
                    "Amazon Web Services provides cloud computing services including S3 for object storage.");
            return material.getId();
        });
        assertThat(materialId).isNotNull();

        Material material = materials.findById(materialId).orElseThrow();
        UUID userId = spaces.findById(material.getSpaceId()).orElseThrow().getUserId();

        ingestion.onMaterialUploaded(new MaterialUploadedEvent(materialId, userId));

        Material indexed = materials.findById(materialId).orElseThrow();
        assertThat(indexed.getStatus()).isEqualTo(MaterialStatus.READY);

        Filter.Expression userFilter = new FilterExpressionBuilder()
                .eq("user_id", userId.toString())
                .build();
        List<Document> hits = vectorStore.similaritySearch(SearchRequest.builder()
                .query("cloud computing object storage")
                .topK(3)
                .filterExpression(userFilter)
                .build());

        assertThat(hits).isNotEmpty();
        assertThat(hits).allSatisfy(doc -> {
            assertThat(doc.getMetadata()).containsEntry("user_id", userId.toString());
            assertThat(doc.getMetadata()).containsEntry("material_id", materialId.toString());
            assertThat(doc.getMetadata()).containsEntry("material_name", "AWS notes");
        });
    }

    private User persistUser(String email) {
        User user = new User();
        user.setUsername(email);
        user.setStatus(UserStatus.ACTIVE);
        return users.saveAndFlush(user);
    }

    private Space persistSpace(User user, String name) {
        Space space = new Space();
        space.setUserId(user.getId());
        space.setName(name);
        return spaces.saveAndFlush(space);
    }

    private Topic persistTopic(Space space, String name) {
        Topic topic = new Topic();
        topic.setSpaceId(space.getId());
        topic.setName(name);
        return topics.saveAndFlush(topic);
    }

    private Material persistMaterial(Topic topic, String title, String content) {
        Material material = new Material();
        material.setSpaceId(topic.getSpaceId());
        material.setTopic(topic);
        material.setTitle(title);
        material.setContentType("text/plain; charset=utf-8");
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        material.setSizeBytes((long) bytes.length);
        material.setStatus(MaterialStatus.PENDING);
        material = materials.saveAndFlush(material);
        try {
            ((InMemoryBlobStorage) storage).put(MaterialStorageKeys.forCoordinates(material.getSpaceId(), material.getId()),
                    new ByteArrayInputStream(bytes), bytes.length, "text/plain");
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
        return material;
    }
}
