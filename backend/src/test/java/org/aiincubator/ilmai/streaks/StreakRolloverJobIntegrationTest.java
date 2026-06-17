package org.aiincubator.ilmai.streaks;

import org.aiincubator.ilmai.ai.ingestion.support.IntegrationTestConfiguration;
import org.aiincubator.ilmai.auth.UserStatus;
import org.aiincubator.ilmai.auth.domain.User;
import org.aiincubator.ilmai.auth.domain.UserRepository;
import org.aiincubator.ilmai.streaks.service.StreakRolloverJob;
import org.aiincubator.ilmai.streaks.service.StreakService;
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
import java.time.LocalDate;
import java.time.OffsetDateTime;
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
class StreakRolloverJobIntegrationTest {

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
    private StreakRolloverJob streakRolloverJob;

    @Autowired
    private StreakService streakService;

    @Autowired
    private StreaksApi streaksApi;

    @Autowired
    private UserRepository users;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void rolloverJobRollsActiveUserAndLeavesInactiveUserIsolated() {
        when(clock.instant()).thenReturn(Instant.parse("2026-06-02T06:00:00Z"));
        UUID userA = seedUser();
        UUID userB = seedUser();

        streakService.recordActivity(userA, OffsetDateTime.parse("2026-06-01T12:00:00Z"));
        streakService.recordActivity(userB, OffsetDateTime.parse("2026-05-20T12:00:00Z"));

        streakRolloverJob.run();

        StreakDto a = streaksApi.getStreak(userA);
        assertThat(a.getStreakCurrent()).isEqualTo(1);
        assertThat(a.getStreakLongest()).isEqualTo(1);
        assertThat(a.getStreakLastDay()).isEqualTo(LocalDate.parse("2026-06-01"));

        assertThat(streaksApi.getStreak(userB).getStreakCurrent()).isZero();
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
