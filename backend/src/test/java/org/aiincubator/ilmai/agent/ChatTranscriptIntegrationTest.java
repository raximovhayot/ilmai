package org.aiincubator.ilmai.agent;

import org.aiincubator.ilmai.agent.api.ChatMessageResponse;
import org.aiincubator.ilmai.agent.service.ChatSessionException;
import org.aiincubator.ilmai.agent.service.ChatSessionService;
import org.aiincubator.ilmai.agent.service.ChatTranscriptService;
import org.aiincubator.ilmai.ai.ingestion.support.IntegrationTestConfiguration;
import org.aiincubator.ilmai.auth.UserStatus;
import org.aiincubator.ilmai.auth.domain.User;
import org.aiincubator.ilmai.auth.domain.UserRepository;
import org.aiincubator.ilmai.common.CurrentUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
class ChatTranscriptIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("ilmai_test")
            .withUsername("test")
            .withPassword("test");

    @Autowired
    private ChatSessionService chatSessionService;

    @Autowired
    private ChatTranscriptService chatTranscriptService;

    @Autowired
    private UserRepository users;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void recordedTurnsAreReadableInOrderWithCitations() {
        CurrentUser owner = new CurrentUser(seedUser());
        UUID sessionId = chatSessionService.create(owner, null).getId();

        chatTranscriptService.recordUserTurn(owner, sessionId, "What is osmosis?");
        chatTranscriptService.recordAssistantTurn(owner, sessionId, "Osmosis is water movement [1].",
                List.of(new RetrievedChunk(UUID.randomUUID(), "Biology.pdf", 3, "water moves across a membrane", 0.91)),
                false);

        List<ChatMessageResponse> history = chatTranscriptService.getMessages(owner, sessionId);

        assertThat(history).hasSize(2);
        assertThat(history.get(0).getRole()).isEqualTo(ChatMessageRole.USER);
        assertThat(history.get(0).getContent()).isEqualTo("What is osmosis?");
        assertThat(history.get(1).getRole()).isEqualTo(ChatMessageRole.ASSISTANT);
        assertThat(history.get(1).getContent()).isEqualTo("Osmosis is water movement [1].");
        assertThat(history.get(1).getCitations()).singleElement()
                .satisfies(citation -> {
                    assertThat(citation.getMaterialName()).isEqualTo("Biology.pdf");
                    assertThat(citation.getLocator()).isEqualTo("t3");
                });
    }

    @Test
    void blankTurnsAreNotPersisted() {
        CurrentUser owner = new CurrentUser(seedUser());
        UUID sessionId = chatSessionService.create(owner, null).getId();

        chatTranscriptService.recordUserTurn(owner, sessionId, "   ");
        chatTranscriptService.recordAssistantTurn(owner, sessionId, "", List.of(), false);

        assertThat(chatTranscriptService.getMessages(owner, sessionId)).isEmpty();
    }

    @Test
    void historyIsScopedToTheSessionOwner() {
        CurrentUser owner = new CurrentUser(seedUser());
        CurrentUser stranger = new CurrentUser(seedUser());
        UUID sessionId = chatSessionService.create(owner, null).getId();
        chatTranscriptService.recordUserTurn(owner, sessionId, "private question");

        assertThatThrownBy(() -> chatTranscriptService.getMessages(stranger, sessionId))
                .isInstanceOf(ChatSessionException.class);
    }

    private UUID seedUser() {
        return transactionTemplate.execute(status -> {
            User user = new User();
            user.setUsername("user-" + UUID.randomUUID() + "@example.com");
            user.setStatus(UserStatus.ACTIVE);
            return users.saveAndFlush(user).getId();
        });
    }
}
