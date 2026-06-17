package org.aiincubator.ilmai.streaks;

import org.aiincubator.ilmai.common.UserActivityRecordedEvent;
import org.aiincubator.ilmai.ai.ingestion.support.IntegrationTestConfiguration;
import org.aiincubator.ilmai.auth.UserStatus;
import org.aiincubator.ilmai.auth.domain.User;
import org.aiincubator.ilmai.auth.domain.UserRepository;
import org.aiincubator.ilmai.streaks.domain.StreakActivityDayRepository;
import org.aiincubator.ilmai.streaks.service.StreakService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDate;
import java.time.OffsetDateTime;
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
class StreaksIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("ilmai_test")
            .withUsername("test")
            .withPassword("test");

    @Autowired
    private ApplicationEventPublisher events;

    @Autowired
    private StreakService streakService;

    @Autowired
    private StreaksApi streaksApi;

    @Autowired
    private StreakActivityDayRepository activityDays;

    @Autowired
    private UserRepository users;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void activityEventUpsertsStreakDayPerUserAndIsIdempotent() {
        UUID userA = seedUser();
        UUID userB = seedUser();
        OffsetDateTime occurredAt = OffsetDateTime.parse("2026-06-01T12:00:00Z");

        events.publishEvent(new UserActivityRecordedEvent(userA, occurredAt));
        events.publishEvent(new UserActivityRecordedEvent(userA, occurredAt));

        assertThat(activityDays.countByUserId(userA)).isEqualTo(1);
        assertThat(activityDays.existsByUserIdAndActivityDate(userA, LocalDate.parse("2026-06-01"))).isTrue();
        assertThat(activityDays.countByUserId(userB)).isZero();
    }

    @Test
    void rolloverCountsConsecutiveDaysThenBreaksOnGapIsolatedPerUser() {
        UUID userA = seedUser();
        UUID userB = seedUser();

        streakService.recordActivity(userA, OffsetDateTime.parse("2026-06-01T12:00:00Z"));
        streakService.recordActivity(userA, OffsetDateTime.parse("2026-06-02T12:00:00Z"));

        streakService.rollover(userA, LocalDate.parse("2026-06-01"));
        streakService.rollover(userA, LocalDate.parse("2026-06-02"));

        StreakDto afterTwoDays = streaksApi.getStreak(userA);
        assertThat(afterTwoDays.getStreakCurrent()).isEqualTo(2);
        assertThat(afterTwoDays.getStreakLongest()).isEqualTo(2);
        assertThat(afterTwoDays.getStreakLastDay()).isEqualTo(LocalDate.parse("2026-06-02"));

        assertThat(streaksApi.getStreak(userB).getStreakCurrent()).isZero();

        streakService.rollover(userA, LocalDate.parse("2026-06-03"));

        StreakDto afterGap = streaksApi.getStreak(userA);
        assertThat(afterGap.getStreakCurrent()).isZero();
        assertThat(afterGap.getStreakBrokenAt()).isEqualTo(LocalDate.parse("2026-06-03"));
        assertThat(afterGap.getStreakLongest()).isEqualTo(2);
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
