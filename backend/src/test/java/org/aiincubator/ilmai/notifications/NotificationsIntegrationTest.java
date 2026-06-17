package org.aiincubator.ilmai.notifications;

import org.aiincubator.ilmai.ai.ingestion.support.IntegrationTestConfiguration;
import org.aiincubator.ilmai.auth.UserStatus;
import org.aiincubator.ilmai.auth.domain.User;
import org.aiincubator.ilmai.auth.domain.UserRepository;
import org.aiincubator.ilmai.common.i18n.SupportedLocale;
import org.aiincubator.ilmai.notifications.service.DailyReminderJob;
import org.aiincubator.ilmai.profiles.domain.Profile;
import org.aiincubator.ilmai.profiles.domain.ProfileRepository;
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
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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
class NotificationsIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("ilmai_test")
            .withUsername("test")
            .withPassword("test");

    @MockitoBean
    private Clock clock;

    @Autowired
    private NotificationsApi notificationsApi;

    @Autowired
    private DailyReminderJob dailyReminderJob;

    @Autowired
    private UserRepository users;

    @Autowired
    private ProfileRepository profiles;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void enqueueIsIdempotentOnDedupeKey() {
        UUID user = seedUser();
        OffsetDateTime now = OffsetDateTime.parse("2026-06-01T09:00:00Z");
        OutboxMessageRequest request = request(user, "dup:" + user, now);

        notificationsApi.enqueue(request);
        notificationsApi.enqueue(request);

        List<OutboxMessageDto> mine = pendingFor(user, now.plusHours(1));
        assertThat(mine).hasSize(1);
        assertThat(mine.get(0).getBody()).isEqualTo("body");
    }

    @Test
    void findPendingReturnsDueUnsentAndExcludesFuture() {
        UUID user = seedUser();
        OffsetDateTime base = OffsetDateTime.parse("2026-06-01T09:00:00Z");
        notificationsApi.enqueue(request(user, "due:" + user, base));
        notificationsApi.enqueue(request(user, "future:" + user, base.plusHours(2)));

        List<OutboxMessageDto> due = pendingFor(user, base.plusHours(1));

        assertThat(due).extracting(OutboxMessageDto::getDedupeKey).containsExactly("due:" + user);
    }

    @Test
    void dailyReminderJobEnqueuesForConfiguredUserAtTheirHourOnly() {
        when(clock.instant()).thenReturn(Instant.parse("2026-06-01T09:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);

        UUID withReminder = seedUserWithReminder(LocalTime.of(9, 0));
        UUID withoutReminder = seedUser();

        dailyReminderJob.run();

        List<OutboxMessageDto> pending = notificationsApi.findPending(OffsetDateTime.parse("2026-06-01T10:00:00Z"));
        assertThat(pending).anyMatch(m -> m.getUserId().equals(withReminder)
                && m.getType() == OutboxMessageType.DAILY_REMINDER);
        assertThat(pending).noneMatch(m -> m.getUserId().equals(withoutReminder));

        OutboxMessageDto reminder = pending.stream()
                .filter(m -> m.getUserId().equals(withReminder))
                .findFirst()
                .orElseThrow();
        assertThat(reminder.getChannel()).isEqualTo(OutboxChannel.TELEGRAM);
        assertThat(reminder.getBody()).isNotBlank();
    }

    private List<OutboxMessageDto> pendingFor(UUID user, OffsetDateTime asOf) {
        return notificationsApi.findPending(asOf).stream()
                .filter(m -> m.getUserId().equals(user))
                .toList();
    }

    private OutboxMessageRequest request(UUID user, String dedupeKey, OffsetDateTime scheduledFor) {
        return OutboxMessageRequest.builder()
                .userId(user)
                .channel(OutboxChannel.TELEGRAM)
                .type(OutboxMessageType.DAILY_REMINDER)
                .body("body")
                .dedupeKey(dedupeKey)
                .scheduledFor(scheduledFor)
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

    private UUID seedUserWithReminder(LocalTime reminder) {
        UUID userId = seedUser();
        transactionTemplate.executeWithoutResult(status -> {
            Profile profile = new Profile();
            profile.setUserId(userId);
            profile.setLocale(SupportedLocale.EN);
            profile.setTimezone("UTC");
            profile.setDailyReminder(reminder);
            profiles.saveAndFlush(profile);
        });
        return userId;
    }
}
