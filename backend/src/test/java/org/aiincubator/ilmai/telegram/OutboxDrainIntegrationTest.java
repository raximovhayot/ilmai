package org.aiincubator.ilmai.telegram;

import org.aiincubator.ilmai.ai.ingestion.support.IntegrationTestConfiguration;
import org.aiincubator.ilmai.auth.UserStatus;
import org.aiincubator.ilmai.auth.domain.User;
import org.aiincubator.ilmai.auth.domain.UserRepository;
import org.aiincubator.ilmai.notifications.NotificationsApi;
import org.aiincubator.ilmai.notifications.OutboxChannel;
import org.aiincubator.ilmai.notifications.OutboxMessageDto;
import org.aiincubator.ilmai.notifications.OutboxMessageRequest;
import org.aiincubator.ilmai.notifications.OutboxMessageType;
import org.aiincubator.ilmai.telegram.domain.TelegramLink;
import org.aiincubator.ilmai.telegram.domain.TelegramLinkRepository;
import org.aiincubator.ilmai.telegram.service.OutboxDrainService;
import org.aiincubator.ilmai.telegram.service.TelegramApiClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
class OutboxDrainIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("ilmai_test")
            .withUsername("test")
            .withPassword("test");

    @MockitoBean
    private Clock clock;

    @MockitoBean
    private TelegramApiClient telegramApiClient;

    @Autowired
    private NotificationsApi notificationsApi;

    @Autowired
    private OutboxDrainService outboxDrainService;

    @Autowired
    private UserRepository users;

    @Autowired
    private TelegramLinkRepository links;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void drainDeliversToLinkedUserMarksItSentAndLeavesUnlinkedPending() {
        when(clock.instant()).thenReturn(Instant.parse("2026-06-01T09:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        when(telegramApiClient.isEnabled()).thenReturn(true);
        when(telegramApiClient.sendMessage(anyLong(), anyString())).thenReturn(true);

        UUID linkedUser = seedUser();
        linkChat(linkedUser, 111L);
        UUID unlinkedUser = seedUser();

        UUID linkedMessageId = notificationsApi.enqueue(reminder(linkedUser, "linked-body")).getId();
        UUID unlinkedMessageId = notificationsApi.enqueue(reminder(unlinkedUser, "unlinked-body")).getId();

        int delivered = outboxDrainService.drain();

        assertThat(delivered).isEqualTo(1);

        List<UUID> stillPending = notificationsApi.findPending(OffsetDateTime.parse("2026-06-01T10:00:00Z"))
                .stream()
                .map(OutboxMessageDto::getId)
                .toList();
        assertThat(stillPending).doesNotContain(linkedMessageId).contains(unlinkedMessageId);

        verify(telegramApiClient).sendMessage(eq(111L), eq("linked-body"));
        verify(telegramApiClient, times(1)).sendMessage(anyLong(), anyString());
    }

    private OutboxMessageRequest reminder(UUID userId, String body) {
        return OutboxMessageRequest.builder()
                .userId(userId)
                .channel(OutboxChannel.TELEGRAM)
                .type(OutboxMessageType.DAILY_REMINDER)
                .body(body)
                .dedupeKey("reminder:" + userId)
                .scheduledFor(OffsetDateTime.parse("2026-06-01T08:00:00Z"))
                .build();
    }

    private UUID seedUser() {
        return transactionTemplate.execute(status -> {
            User user = new User();
            user.setUsername("user-" + UUID.randomUUID() + "@example.com");
            user.setStatus(UserStatus.ACTIVE);
            return users.saveAndFlush(user).getId();
        });
    }

    private void linkChat(UUID userId, long chatId) {
        transactionTemplate.executeWithoutResult(status -> {
            TelegramLink link = new TelegramLink();
            link.setUserId(userId);
            link.setChatId(chatId);
            link.setLinkedAt(OffsetDateTime.parse("2026-05-01T00:00:00Z"));
            links.saveAndFlush(link);
        });
    }
}
