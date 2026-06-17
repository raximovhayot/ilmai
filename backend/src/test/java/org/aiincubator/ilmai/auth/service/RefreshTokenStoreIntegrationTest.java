package org.aiincubator.ilmai.auth.service;

import org.aiincubator.ilmai.ai.ingestion.support.IntegrationTestConfiguration;
import org.aiincubator.ilmai.auth.UserStatus;
import org.aiincubator.ilmai.auth.config.AuthProperties;
import org.aiincubator.ilmai.auth.domain.RefreshToken;
import org.aiincubator.ilmai.auth.domain.RefreshTokenRepository;
import org.aiincubator.ilmai.auth.domain.RefreshTokenStatus;
import org.aiincubator.ilmai.auth.domain.User;
import org.aiincubator.ilmai.auth.domain.UserRepository;
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

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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
class RefreshTokenStoreIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("ilmai_test")
            .withUsername("test")
            .withPassword("test");

    @Autowired
    private RefreshTokenStore store;

    @Autowired
    private RefreshTokenRepository refreshTokens;

    @Autowired
    private RefreshTokenCleanupJob cleanupJob;

    @Autowired
    private AuthProperties props;

    @Autowired
    private UserRepository users;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void rotationHappyPathConsumesExactlyOnce() {
        UUID userId = seedUser();
        UUID familyId = UUID.randomUUID();
        String jti = newJti();
        store.recordIssued(jti, userId, familyId, Instant.now().plusSeconds(3600));

        assertThat(store.consume(jti, familyId, Instant.now().plusSeconds(3600)))
                .isEqualTo(RefreshTokenStore.ConsumeResult.SUCCESS);
        assertThat(store.isFamilyRevoked(familyId)).isFalse();
    }

    @Test
    void replayedTokenIsReusedThenFamilyRevocationBlocksEverything() {
        UUID userId = seedUser();
        UUID familyId = UUID.randomUUID();
        String oldJti = newJti();
        String newJti = newJti();
        Instant expiresAt = Instant.now().plusSeconds(3600);
        store.recordIssued(oldJti, userId, familyId, expiresAt);

        assertThat(store.consume(oldJti, familyId, expiresAt)).isEqualTo(RefreshTokenStore.ConsumeResult.SUCCESS);
        store.recordIssued(newJti, userId, familyId, expiresAt);

        assertThat(store.consume(oldJti, familyId, expiresAt)).isEqualTo(RefreshTokenStore.ConsumeResult.REUSED);

        store.revokeFamily(familyId);
        assertThat(store.isFamilyRevoked(familyId)).isTrue();

        assertThat(store.consume(newJti, familyId, expiresAt)).isEqualTo(RefreshTokenStore.ConsumeResult.UNKNOWN);
        assertThat(store.consume(oldJti, familyId, expiresAt)).isEqualTo(RefreshTokenStore.ConsumeResult.UNKNOWN);
    }

    @Test
    void logoutRevokesActiveTokenAndLaterConsumeIsUnknown() {
        UUID userId = seedUser();
        UUID familyId = UUID.randomUUID();
        String jti = newJti();
        Instant expiresAt = Instant.now().plusSeconds(3600);
        store.recordIssued(jti, userId, familyId, expiresAt);

        assertThat(store.revokeActive(jti)).isTrue();
        assertThat(store.revokeActive(jti)).isFalse();
        assertThat(store.consume(jti, familyId, expiresAt)).isEqualTo(RefreshTokenStore.ConsumeResult.UNKNOWN);
        assertThat(store.isFamilyRevoked(familyId)).isFalse();
    }

    @Test
    void expiredTokenIsUnknown() {
        UUID userId = seedUser();
        UUID familyId = UUID.randomUUID();
        String jti = newJti();
        seedToken(jti, userId, familyId, RefreshTokenStatus.ACTIVE,
                OffsetDateTime.now().minusMinutes(5), null);

        assertThat(store.consume(jti, familyId, Instant.now().minusSeconds(300)))
                .isEqualTo(RefreshTokenStore.ConsumeResult.UNKNOWN);
    }

    @Test
    void unknownJtiIsUnknown() {
        assertThat(store.consume(newJti(), UUID.randomUUID(), Instant.now().plusSeconds(3600)))
                .isEqualTo(RefreshTokenStore.ConsumeResult.UNKNOWN);
    }

    @Test
    void parallelConsumeOfOneJtiHasExactlyOneWinner() throws Exception {
        UUID userId = seedUser();
        UUID familyId = UUID.randomUUID();
        String jti = newJti();
        Instant expiresAt = Instant.now().plusSeconds(3600);
        store.recordIssued(jti, userId, familyId, expiresAt);

        int attempts = 8;
        CountDownLatch ready = new CountDownLatch(attempts);
        CountDownLatch go = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(attempts)) {
            List<Future<RefreshTokenStore.ConsumeResult>> results = new java.util.ArrayList<>();
            for (int i = 0; i < attempts; i++) {
                results.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return store.consume(jti, familyId, expiresAt);
                }));
            }
            ready.await();
            go.countDown();

            long successes = 0;
            for (Future<RefreshTokenStore.ConsumeResult> future : results) {
                if (future.get() == RefreshTokenStore.ConsumeResult.SUCCESS) {
                    successes++;
                }
            }
            assertThat(successes).isEqualTo(1);
        }
    }

    @Test
    void cleanupRemovesOnlyExpiredAndStaleRevokedRows() {
        UUID userId = seedUser();
        UUID activeFamily = UUID.randomUUID();
        UUID staleRevokedFamily = UUID.randomUUID();
        UUID freshRevokedFamily = UUID.randomUUID();
        String activeJti = newJti();
        String expiredJti = newJti();
        String staleRevokedJti = newJti();
        String freshRevokedJti = newJti();
        OffsetDateTime now = OffsetDateTime.now();

        seedToken(activeJti, userId, activeFamily, RefreshTokenStatus.ACTIVE, now.plusHours(1), null);
        seedToken(expiredJti, userId, UUID.randomUUID(), RefreshTokenStatus.CONSUMED, now.minusHours(1), null);
        seedToken(staleRevokedJti, userId, staleRevokedFamily, RefreshTokenStatus.REVOKED, now.plusHours(1),
                now.minus(props.getJwt().getAccessTtl()).minusHours(1));
        seedToken(freshRevokedJti, userId, freshRevokedFamily, RefreshTokenStatus.REVOKED, now.minusHours(1),
                now.minusSeconds(30));

        cleanupJob.run();

        List<String> remaining = refreshTokens.findAll().stream().map(RefreshToken::getJti).toList();
        assertThat(remaining).contains(activeJti, freshRevokedJti);
        assertThat(remaining).doesNotContain(expiredJti, staleRevokedJti);
        assertThat(store.isFamilyRevoked(freshRevokedFamily)).isTrue();
        assertThat(store.isFamilyRevoked(staleRevokedFamily)).isFalse();
    }

    private UUID seedUser() {
        return transactionTemplate.execute(status -> {
            User user = new User();
            user.setUsername("user-" + UUID.randomUUID() + "@example.com");
            user.setStatus(UserStatus.ACTIVE);
            return users.saveAndFlush(user).getId();
        });
    }

    private void seedToken(String jti, UUID userId, UUID familyId, RefreshTokenStatus status,
                           OffsetDateTime expiresAt, OffsetDateTime revokedAt) {
        transactionTemplate.executeWithoutResult(tx -> {
            RefreshToken token = new RefreshToken();
            token.setJti(jti);
            token.setFamilyId(familyId);
            token.setUserId(userId);
            token.setStatus(status);
            token.setExpiresAt(expiresAt);
            token.setRevokedAt(revokedAt);
            refreshTokens.saveAndFlush(token);
        });
    }

    private String newJti() {
        return "jti-" + UUID.randomUUID();
    }
}
