package org.aiincubator.ilmai.digest;

import org.aiincubator.ilmai.ai.ingestion.support.IntegrationTestConfiguration;
import org.aiincubator.ilmai.auth.UserStatus;
import org.aiincubator.ilmai.auth.domain.User;
import org.aiincubator.ilmai.auth.domain.UserRepository;
import org.aiincubator.ilmai.agent.DigestNarrationApi;
import org.aiincubator.ilmai.common.i18n.SupportedLocale;
import org.aiincubator.ilmai.digest.domain.WeeklyDigestRepository;
import org.aiincubator.ilmai.digest.service.WeeklyDigestJob;
import org.aiincubator.ilmai.notifications.NotificationsApi;
import org.aiincubator.ilmai.notifications.OutboxMessageDto;
import org.aiincubator.ilmai.notifications.OutboxMessageType;
import org.aiincubator.ilmai.profiles.domain.Profile;
import org.aiincubator.ilmai.profiles.domain.ProfileRepository;
import org.aiincubator.ilmai.streaks.domain.StreakActivityDay;
import org.aiincubator.ilmai.streaks.domain.StreakActivityDayRepository;
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
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
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
        "auth.google.client-id=integration-test-google-client-id",
        "digest.send-hour=19"
})
class WeeklyDigestIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("ilmai_test")
            .withUsername("test")
            .withPassword("test");

    private static final LocalDate SUNDAY =
            LocalDate.of(2026, 6, 1).with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
    private static final Instant NOW = SUNDAY.atTime(19, 0).toInstant(ZoneOffset.UTC);

    @MockitoBean
    private Clock clock;

    @MockitoBean
    private DigestNarrationApi digestNarrationApi;

    @Autowired
    private WeeklyDigestJob weeklyDigestJob;

    @Autowired
    private DigestApi digestApi;

    @Autowired
    private NotificationsApi notificationsApi;

    @Autowired
    private WeeklyDigestRepository weeklyDigests;

    @Autowired
    private UserRepository users;

    @Autowired
    private ProfileRepository profiles;

    @Autowired
    private StreakActivityDayRepository activityDays;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void generatesNewInactiveAndFullVariants() {
        fixClock();
        LocalDate weekMonday = SUNDAY.with(DayOfWeek.MONDAY);

        UUID newUser = seedUser();
        seedActivity(newUser, SUNDAY, SUNDAY.minusDays(1), SUNDAY.minusDays(2));

        UUID inactiveUser = seedUser();
        seedActivityRange(inactiveUser, weekMonday.minusDays(1), 8);

        UUID activeUser = seedUser();
        seedActivityRange(activeUser, weekMonday.minusDays(1), 7);
        seedActivity(activeUser, SUNDAY);

        weeklyDigestJob.run();

        assertThat(variantOf(newUser)).isEqualTo(DigestVariant.NEW_USER);
        assertThat(variantOf(inactiveUser)).isEqualTo(DigestVariant.INACTIVE);
        assertThat(variantOf(activeUser)).isEqualTo(DigestVariant.FULL);

        List<OutboxMessageDto> pending = pendingAsOfNextHour();
        for (UUID user : List.of(newUser, inactiveUser, activeUser)) {
            assertThat(pending).anyMatch(m -> m.getUserId().equals(user)
                    && m.getType() == OutboxMessageType.WEEKLY_DIGEST);
        }
    }

    @Test
    void isIdempotentWithinTheSameIsoWeek() {
        fixClock();
        UUID user = seedUser();
        seedActivity(user, SUNDAY);

        weeklyDigestJob.run();
        weeklyDigestJob.run();

        long rows = weeklyDigests.findAll().stream()
                .filter(d -> d.getUserId().equals(user))
                .count();
        assertThat(rows).isEqualTo(1);
        assertThat(pendingFor(user))
                .filteredOn(m -> m.getType() == OutboxMessageType.WEEKLY_DIGEST)
                .hasSize(1);
    }

    @Test
    void digestIsScopedToTheOwningUser() {
        fixClock();
        LocalDate weekMonday = SUNDAY.with(DayOfWeek.MONDAY);

        UUID active = seedUser();
        seedActivityRange(active, weekMonday.minusDays(1), 7);
        seedActivity(active, SUNDAY);

        UUID idle = seedUser();

        weeklyDigestJob.run();

        WeeklyDigestDto activeDigest = digestApi.getLatestForUser(active).orElseThrow();
        WeeklyDigestDto idleDigest = digestApi.getLatestForUser(idle).orElseThrow();

        assertThat(activeDigest.getUserId()).isEqualTo(active);
        assertThat(activeDigest.getVariant()).isEqualTo(DigestVariant.FULL);
        assertThat(activeDigest.getActiveDays()).isGreaterThan(0);

        assertThat(idleDigest.getUserId()).isEqualTo(idle);
        assertThat(idleDigest.getVariant()).isEqualTo(DigestVariant.NEW_USER);
        assertThat(idleDigest.getActiveDays()).isZero();
    }

    private void fixClock() {
        when(clock.instant()).thenReturn(NOW);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
    }

    private DigestVariant variantOf(UUID userId) {
        return digestApi.getLatestForUser(userId).orElseThrow().getVariant();
    }

    private List<OutboxMessageDto> pendingAsOfNextHour() {
        return notificationsApi.findPending(OffsetDateTime.ofInstant(NOW.plusSeconds(3600), ZoneOffset.UTC));
    }

    private List<OutboxMessageDto> pendingFor(UUID user) {
        return pendingAsOfNextHour().stream()
                .filter(m -> m.getUserId().equals(user))
                .toList();
    }

    private UUID seedUser() {
        return transactionTemplate.execute(status -> {
            User user = new User();
            user.setUsername("user-" + UUID.randomUUID() + "@example.com");
            user.setStatus(UserStatus.ACTIVE);
            UUID userId = users.saveAndFlush(user).getId();
            Profile profile = new Profile();
            profile.setUserId(userId);
            profile.setLocale(SupportedLocale.EN);
            profile.setTimezone("UTC");
            profiles.saveAndFlush(profile);
            return userId;
        });
    }

    private void seedActivityRange(UUID userId, LocalDate lastDay, int count) {
        LocalDate[] dates = new LocalDate[count];
        for (int i = 0; i < count; i++) {
            dates[i] = lastDay.minusDays(i);
        }
        seedActivity(userId, dates);
    }

    private void seedActivity(UUID userId, LocalDate... dates) {
        transactionTemplate.executeWithoutResult(status -> {
            for (LocalDate date : dates) {
                StreakActivityDay day = new StreakActivityDay();
                day.setUserId(userId);
                day.setActivityDate(date);
                activityDays.saveAndFlush(day);
            }
        });
    }
}
