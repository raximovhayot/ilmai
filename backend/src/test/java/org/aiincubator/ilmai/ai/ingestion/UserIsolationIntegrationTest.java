package org.aiincubator.ilmai.ai.ingestion;

import org.aiincubator.ilmai.ai.ingestion.support.AbstractEmbeddingIntegrationTest;
import org.aiincubator.ilmai.ai.ingestion.support.InMemoryBlobStorage;
import org.aiincubator.ilmai.ai.ingestion.support.UserMaterialFixture;
import org.aiincubator.ilmai.auth.domain.User;
import org.aiincubator.ilmai.auth.domain.UserRepository;
import org.aiincubator.ilmai.auth.UserStatus;
import org.aiincubator.ilmai.common.storage.BlobStorage;
import org.aiincubator.ilmai.materials.domain.Material;
import org.aiincubator.ilmai.materials.domain.MaterialRepository;
import org.aiincubator.ilmai.materials.MaterialStatus;
import org.aiincubator.ilmai.materials.MaterialDeletedEvent;
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

class UserIsolationIntegrationTest extends AbstractEmbeddingIntegrationTest {

    @Autowired UserRepository users;
    @Autowired RoomRepository rooms;
    @Autowired TopicRepository topics;
    @Autowired MaterialRepository materials;
    @Autowired MaterialIngestionService ingestion;
    @Autowired MaterialChunkCleanupService cleanupService;
    @Autowired BlobStorage storage;
    @Autowired VectorStore vectorStore;
    @Autowired TransactionTemplate transactionTemplate;

    @Test
    void similaritySearch_isScopedByUserIdMetadata() {
        UserMaterialFixture userA = setupUserWithMaterial("alice@example.com", "Alice Space", "Alice Topic",
                "Alice notes", "Quantum entanglement and superposition govern qubits in quantum computers.");
        UserMaterialFixture userB = setupUserWithMaterial("bob@example.com", "Bob Space", "Bob Topic",
                "Bob notes", "Newton's laws describe classical mechanics for everyday physics problems.");

        ingestion.onMaterialUploaded(new MaterialUploadedEvent(userA.getMaterialId(), userA.getUserId()));
        ingestion.onMaterialUploaded(new MaterialUploadedEvent(userB.getMaterialId(), userB.getUserId()));

        assertMaterialReady(userA.getMaterialId());
        assertMaterialReady(userB.getMaterialId());

        List<Document> aliceHits = vectorStore.similaritySearch(SearchRequest.builder()
                .query("Newton classical mechanics")
                .topK(5)
                .filterExpression(eqUser(userA.getUserId()))
                .build());
        assertThat(aliceHits).allSatisfy(doc ->
                assertThat(doc.getMetadata()).containsEntry("user_id", userA.getUserId().toString()));
        assertThat(aliceHits).noneSatisfy(doc ->
                assertThat(doc.getMetadata()).containsEntry("material_id", userB.getMaterialId().toString()));

        List<Document> bobHits = vectorStore.similaritySearch(SearchRequest.builder()
                .query("Newton classical mechanics")
                .topK(5)
                .filterExpression(eqUser(userB.getUserId()))
                .build());
        assertThat(bobHits).isNotEmpty();
        assertThat(bobHits).anySatisfy(doc ->
                assertThat(doc.getMetadata()).containsEntry("material_id", userB.getMaterialId().toString()));
    }

    @Test
    void chunkCleanup_removesOnlyDeletedMaterialChunks() {
        UserMaterialFixture userA = setupUserWithMaterial("carol@example.com", "Carol Space", "Carol Topic",
                "Carol notes", "Photosynthesis converts sunlight into chemical energy for plant growth.");
        UserMaterialFixture userB = setupUserWithMaterial("dave@example.com", "Dave Space", "Dave Topic",
                "Dave notes", "Mitochondria are the powerhouse of the cell in animal biology classes.");

        ingestion.onMaterialUploaded(new MaterialUploadedEvent(userA.getMaterialId(), userA.getUserId()));
        ingestion.onMaterialUploaded(new MaterialUploadedEvent(userB.getMaterialId(), userB.getUserId()));

        cleanupService.onMaterialDeleted(new MaterialDeletedEvent(userA.getMaterialId(), userA.getUserId()));

        List<Document> aliceLeftovers = vectorStore.similaritySearch(SearchRequest.builder()
                .query("photosynthesis sunlight")
                .topK(5)
                .filterExpression(eqMaterial(userA.getMaterialId()))
                .build());
        assertThat(aliceLeftovers).isEmpty();

        List<Document> bobChunks = vectorStore.similaritySearch(SearchRequest.builder()
                .query("mitochondria cell biology")
                .topK(5)
                .filterExpression(eqMaterial(userB.getMaterialId()))
                .build());
        assertThat(bobChunks).isNotEmpty();
    }

    private Filter.Expression eqUser(UUID userId) {
        return new FilterExpressionBuilder().eq("user_id", userId.toString()).build();
    }

    private Filter.Expression eqMaterial(UUID materialId) {
        return new FilterExpressionBuilder().eq("material_id", materialId.toString()).build();
    }

    private void assertMaterialReady(UUID materialId) {
        Material material = materials.findById(materialId).orElseThrow();
        assertThat(material.getStatus()).isEqualTo(MaterialStatus.READY);
    }

    private UserMaterialFixture setupUserWithMaterial(String email, String spaceName, String topicName,
                                                      String title, String content) {
        return transactionTemplate.execute(status -> {
            User user = new User();
            user.setUsername(email);
            user.setStatus(UserStatus.ACTIVE);
            user = users.saveAndFlush(user);

            Room space = new Room();
            space.setOwnerId(user.getId());
            space.setName(spaceName);
            space.setPersonal(true);
            space = rooms.saveAndFlush(space);

            Topic topic = new Topic();
            topic.setRoomId(space.getId());
            topic.setName(topicName);
            topic = topics.saveAndFlush(topic);

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

            return new UserMaterialFixture(user.getId(), material.getId());
        });
    }
}
