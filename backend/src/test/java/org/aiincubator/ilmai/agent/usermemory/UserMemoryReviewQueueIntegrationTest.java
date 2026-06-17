package org.aiincubator.ilmai.agent.usermemory;

import org.aiincubator.ilmai.ai.ingestion.support.IntegrationTestConfiguration;
import org.aiincubator.ilmai.auth.UserStatus;
import org.aiincubator.ilmai.auth.domain.User;
import org.aiincubator.ilmai.auth.domain.UserRepository;
import org.aiincubator.ilmai.common.CurrentUser;
import org.aiincubator.ilmai.quiz.QuizAnswerGradedEvent;
import org.aiincubator.ilmai.agent.ReviewDueDto;
import org.aiincubator.ilmai.agent.UserMemoryApi;
import org.aiincubator.ilmai.agent.usermemory.domain.ReviewQueueEntry;
import org.aiincubator.ilmai.agent.usermemory.domain.ReviewQueueRepository;
import org.aiincubator.ilmai.agent.usermemory.domain.ReviewStatus;
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

import java.time.OffsetDateTime;
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
class UserMemoryReviewQueueIntegrationTest {

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
    private ReviewQueueRepository reviewQueue;

    @Autowired
    private UserMemoryApi userMemoryApi;

    @Autowired
    private UserRepository users;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void wrongAnswerSchedulesReviewPlusOneDayAndIsolatedPerUser() {
        UUID userA = seedUser();
        UUID userB = seedUser();
        OffsetDateTime gradedAt = OffsetDateTime.parse("2026-05-31T10:00:00Z");

        transactionTemplate.executeWithoutResult(status -> events.publishEvent(new QuizAnswerGradedEvent(
                userA, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "photosynthesis", false, gradedAt)));

        List<ReviewQueueEntry> aRows = reviewQueue.findByUserIdOrderByNextReviewAtAsc(userA);
        assertThat(aRows).hasSize(1);
        ReviewQueueEntry row = aRows.get(0);
        assertThat(row.getConcept()).isEqualTo("photosynthesis");
        assertThat(row.getStatus()).isEqualTo(ReviewStatus.ACTIVE);
        assertThat(row.getIntervalIndex()).isZero();
        assertThat(row.getTimesWrong()).isEqualTo(1);
        assertThat(row.getNextReviewAt().toInstant()).isEqualTo(gradedAt.plusDays(1).toInstant());

        assertThat(reviewQueue.findByUserIdOrderByNextReviewAtAsc(userB)).isEmpty();
    }

    @Test
    void correctAnswerWithNoPriorMissDoesNotCreateEntry() {
        UUID user = seedUser();
        OffsetDateTime gradedAt = OffsetDateTime.parse("2026-05-31T10:00:00Z");

        transactionTemplate.executeWithoutResult(status -> events.publishEvent(new QuizAnswerGradedEvent(
                user, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "mitosis", true, gradedAt)));

        assertThat(reviewQueue.findByUserIdOrderByNextReviewAtAsc(user)).isEmpty();
    }

    @Test
    void dueReviewsReturnsOnlyActiveDueRowsScopedToUser() {
        UUID userA = seedUser();
        UUID userB = seedUser();
        OffsetDateTime asOf = OffsetDateTime.parse("2026-06-01T10:00:00Z");

        transactionTemplate.executeWithoutResult(status -> {
            saveEntry(userA, "due-now", ReviewStatus.ACTIVE, asOf.minusHours(1));
            saveEntry(userA, "future", ReviewStatus.ACTIVE, asOf.plusDays(2));
            saveEntry(userA, "mastered", ReviewStatus.MASTERED, asOf.minusDays(5));
            saveEntry(userB, "other-user", ReviewStatus.ACTIVE, asOf.minusDays(1));
        });

        List<ReviewDueDto> dueA = userMemoryApi.dueReviews(new CurrentUser(userA), asOf);
        assertThat(dueA).hasSize(1);
        assertThat(dueA.get(0).getConcept()).isEqualTo("due-now");
        assertThat(userMemoryApi.countDueReviews(userA, asOf)).isEqualTo(1);

        assertThat(userMemoryApi.dueReviews(new CurrentUser(userB), asOf))
                .extracting(ReviewDueDto::getConcept)
                .containsExactly("other-user");
        assertThat(userMemoryApi.countDueReviews(userB, asOf)).isEqualTo(1);
    }

    private void saveEntry(UUID userId, String concept, ReviewStatus status, OffsetDateTime nextReviewAt) {
        ReviewQueueEntry entry = new ReviewQueueEntry();
        entry.setUserId(userId);
        entry.setConcept(concept);
        entry.setStatus(status);
        entry.setNextReviewAt(nextReviewAt);
        reviewQueue.save(entry);
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
