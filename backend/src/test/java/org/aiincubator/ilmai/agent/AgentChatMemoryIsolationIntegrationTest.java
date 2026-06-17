package org.aiincubator.ilmai.agent;

import org.aiincubator.ilmai.ai.ingestion.support.IntegrationTestConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@Import(IntegrationTestConfiguration.class)
@TestPropertySource(properties = {
        "ai.embedding.api-key=integration-test-key",
        "spring.ai.google.genai.api-key=integration-test-google-genai-key",
        "spring.docker.compose.enabled=false",
        "auth.jwt.secret=integration-test-jwt-secret-32-chars-long-x",
        "auth.google.client-id=integration-test-google-client-id"
})
class AgentChatMemoryIsolationIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("ilmai_test")
            .withUsername("test")
            .withPassword("test");

    @Autowired
    private ChatMemoryRepository chatMemoryRepository;

    @Test
    void persistsAndIsolatesMemoryByConversation() {
        String conversationA = UUID.randomUUID().toString();
        String conversationB = UUID.randomUUID().toString();

        chatMemoryRepository.saveAll(conversationA, List.of(
                new UserMessage("photosynthesis converts light"),
                new AssistantMessage("Yes, into chemical energy.")));
        chatMemoryRepository.saveAll(conversationB, List.of(
                new UserMessage("unrelated-topic-xyz")));

        List<Message> recalledA = chatMemoryRepository.findByConversationId(conversationA);
        assertThat(recalledA).extracting(Message::getText)
                .anyMatch(text -> text.contains("photosynthesis converts light"))
                .noneMatch(text -> text.contains("unrelated-topic-xyz"));

        List<Message> recalledB = chatMemoryRepository.findByConversationId(conversationB);
        assertThat(recalledB).extracting(Message::getText)
                .anyMatch(text -> text.contains("unrelated-topic-xyz"))
                .noneMatch(text -> text.contains("photosynthesis converts light"));
    }
}
