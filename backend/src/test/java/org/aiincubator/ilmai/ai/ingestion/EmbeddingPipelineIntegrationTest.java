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
import org.aiincubator.ilmai.rooms.domain.Room;
import org.aiincubator.ilmai.rooms.domain.RoomRepository;
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
    @Autowired RoomRepository rooms;
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
            Room space = persistSpace(user, "My Space");
            Topic topic = persistTopic(space, "Cloud Computing");
            Material material = persistMaterial(topic, "AWS notes",
                    "Amazon Web Services provides cloud computing services including S3 for object storage.");
            return material.getId();
        });
        assertThat(materialId).isNotNull();

        Material material = materials.findById(materialId).orElseThrow();
        UUID userId = rooms.findById(material.getRoomId()).orElseThrow().getOwnerId();

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
            assertThat(doc.getMetadata()).containsEntry("room_id", material.getRoomId().toString());
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

    private Room persistSpace(User user, String name) {
        Room space = new Room();
        space.setOwnerId(user.getId());
        space.setName(name);
        space.setPersonal(true);
        return rooms.saveAndFlush(space);
    }

    private Topic persistTopic(Room space, String name) {
        Topic topic = new Topic();
        topic.setRoomId(space.getId());
        topic.setName(name);
        return topics.saveAndFlush(topic);
    }

    private Material persistMaterial(Topic topic, String title, String content) {
        Material material = new Material();
        material.setRoomId(topic.getRoomId());
        material.setTopic(topic);
        material.setTitle(title);
        material.setContentType("text/plain; charset=utf-8");
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        material.setSizeBytes((long) bytes.length);
        material.setStatus(MaterialStatus.PENDING);
        material = materials.saveAndFlush(material);
        try {
            ((InMemoryBlobStorage) storage).put(MaterialStorageKeys.forCoordinates(material.getRoomId(), material.getId()),
                    new ByteArrayInputStream(bytes), bytes.length, "text/plain");
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
        return material;
    }
}
