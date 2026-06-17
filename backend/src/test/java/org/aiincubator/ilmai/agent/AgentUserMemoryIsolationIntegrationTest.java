package org.aiincubator.ilmai.agent;

import org.aiincubator.ilmai.agent.service.UserMemoryAdvisor;
import org.aiincubator.ilmai.ai.ingestion.support.IntegrationTestConfiguration;
import org.aiincubator.ilmai.auth.UserStatus;
import org.aiincubator.ilmai.auth.domain.User;
import org.aiincubator.ilmai.auth.domain.UserRepository;
import org.aiincubator.ilmai.common.CurrentUser;
import org.aiincubator.ilmai.agent.UserFactDto;
import org.aiincubator.ilmai.agent.UserMemoryApi;
import org.aiincubator.ilmai.agent.usermemory.domain.UserMemoryFact;
import org.aiincubator.ilmai.agent.usermemory.domain.UserMemoryFactRepository;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

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
class AgentUserMemoryIsolationIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("ilmai_test")
            .withUsername("test")
            .withPassword("test");

    @Autowired
    private UserMemoryAdvisor advisor;

    @Autowired
    private UserMemoryApi userMemoryApi;

    @Autowired
    private UserMemoryFactRepository facts;

    @Autowired
    private UserRepository users;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void factsAreInjectedIntoCoachContextAndIsolatedPerUser() {
        UUID userA = seedUser();
        UUID userB = seedUser();
        seedFact(userA, "user A is preparing for the IELTS exam");
        seedFact(userB, "user B studies organic chemistry");

        assertThat(userMemoryApi.recentFacts(new CurrentUser(userB), 20))
                .extracting(UserFactDto::getContent)
                .contains("user B studies organic chemistry")
                .doesNotContain("user A is preparing for the IELTS exam");

        String systemForB = augmentedSystemFor(userB, UUID.randomUUID().toString());
        assertThat(systemForB).contains("user B studies organic chemistry");
        assertThat(systemForB).doesNotContain("user A is preparing for the IELTS exam");

        String systemForA = augmentedSystemFor(userA, UUID.randomUUID().toString());
        assertThat(systemForA).contains("user A is preparing for the IELTS exam");
        assertThat(systemForA).doesNotContain("user B studies organic chemistry");
    }

    @Test
    void factFromOneSessionIsAvailableToCoachInAnotherSession() {
        UUID user = seedUser();
        seedFact(user, "user mentioned they have a deadline on Friday");

        String firstSession = augmentedSystemFor(user, UUID.randomUUID().toString());
        String secondSession = augmentedSystemFor(user, UUID.randomUUID().toString());

        assertThat(firstSession).contains("user mentioned they have a deadline on Friday");
        assertThat(secondSession).contains("user mentioned they have a deadline on Friday");
    }

    @Test
    void recordedFactsAreWrittenAndIsolatedPerUser() {
        UUID userA = seedUser();
        UUID userB = seedUser();

        int written = userMemoryApi.recordFacts(new CurrentUser(userA),
                List.of("user A prefers worked examples", "user A prefers worked examples", "   "));

        assertThat(written).isEqualTo(1);
        assertThat(userMemoryApi.recentFacts(new CurrentUser(userA), 20))
                .extracting(UserFactDto::getContent)
                .contains("user A prefers worked examples");
        assertThat(userMemoryApi.recentFacts(new CurrentUser(userB), 20))
                .extracting(UserFactDto::getContent)
                .doesNotContain("user A prefers worked examples");
    }

    private String augmentedSystemFor(UUID userId, String sessionId) {
        AtomicReference<ChatClientRequest> seen = new AtomicReference<>();
        CallAdvisorChain chain = new CallAdvisorChain() {
            @Override
            public ChatClientResponse nextCall(ChatClientRequest request) {
                seen.set(request);
                ChatResponse response = ChatResponse.builder()
                        .generations(List.of(new Generation(new AssistantMessage("ok"))))
                        .build();
                return ChatClientResponse.builder()
                        .chatResponse(response)
                        .context(new HashMap<>())
                        .build();
            }

            @Override
            public List<CallAdvisor> getCallAdvisors() {
                return List.of();
            }

            @Override
            public CallAdvisorChain copy(CallAdvisor callAdvisor) {
                return this;
            }
        };

        Map<String, Object> context = new HashMap<>();
        context.put(ChatMemory.CONVERSATION_ID, sessionId);
        context.put(UserMemoryAdvisor.CURRENT_USER_PARAM, new CurrentUser(userId));
        Prompt prompt = new Prompt(List.of(new SystemMessage("You are the Coach."), new UserMessage("hello")));
        advisor.adviseCall(ChatClientRequest.builder().prompt(prompt).context(context).build(), chain);

        return seen.get().prompt().getInstructions().stream()
                .filter(message -> message instanceof SystemMessage)
                .map(Message::getText)
                .findFirst()
                .orElse("");
    }

    private UUID seedUser() {
        return transactionTemplate.execute(status -> {
            User user = new User();
            user.setUsername("user-" + UUID.randomUUID() + "@example.com");
            user.setStatus(UserStatus.ACTIVE);
            return users.saveAndFlush(user).getId();
        });
    }

    private void seedFact(UUID userId, String content) {
        transactionTemplate.executeWithoutResult(status -> {
            UserMemoryFact fact = new UserMemoryFact();
            fact.setUserId(userId);
            fact.setContent(content);
            facts.saveAndFlush(fact);
        });
    }
}
