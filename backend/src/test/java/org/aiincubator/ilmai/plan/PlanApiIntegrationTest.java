package org.aiincubator.ilmai.plan;

import org.aiincubator.ilmai.ai.ingestion.support.IntegrationTestConfiguration;
import org.aiincubator.ilmai.auth.UserStatus;
import org.aiincubator.ilmai.auth.domain.User;
import org.aiincubator.ilmai.auth.domain.UserRepository;
import org.aiincubator.ilmai.common.CurrentUser;
import org.aiincubator.ilmai.materials.MaterialUploadedEvent;
import org.aiincubator.ilmai.plan.domain.LearningPlan;
import org.aiincubator.ilmai.plan.domain.LearningPlanRepository;
import org.aiincubator.ilmai.plan.domain.PlanStep;
import org.aiincubator.ilmai.plan.payload.LearningPlanResponse;
import org.aiincubator.ilmai.plan.payload.PlanStepResponse;
import org.aiincubator.ilmai.plan.service.PlanService;
import org.aiincubator.ilmai.profiles.GoalUpdatedEvent;
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
class PlanApiIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("ilmai_test")
            .withUsername("test")
            .withPassword("test");

    @Autowired
    private PlanApi planApi;

    @Autowired
    private PlanService planService;

    @Autowired
    private LearningPlanRepository plans;

    @Autowired
    private UserRepository users;

    @Autowired
    private ApplicationEventPublisher events;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void activePlanIsScopedToItsOwner() {
        UUID userA = seedUser();
        UUID userB = seedUser();

        planApi.savePlan(new CurrentUser(userA), "IELTS by July",
                LocalDate.now().plusDays(30), List.of(
                        new PlanStepInput(1, LocalDate.now(), "Read unit 1",
                                PlanActivity.READ, List.of(UUID.randomUUID()), "warm up")));
        planApi.savePlan(new CurrentUser(userB), "Organic chemistry",
                null, List.of(
                        new PlanStepInput(1, LocalDate.now(), "Review reactions",
                                PlanActivity.REVIEW, List.of(), null)));

        LearningPlanDto planA = planApi.getActivePlan(new CurrentUser(userA)).orElseThrow();
        LearningPlanDto planB = planApi.getActivePlan(new CurrentUser(userB)).orElseThrow();

        assertThat(planA.getGoal()).isEqualTo("IELTS by July");
        assertThat(planA.getStatus()).isEqualTo(PlanStatus.ACTIVE);
        assertThat(planA.getSteps()).singleElement()
                .satisfies(step -> assertThat(step.getTitle()).isEqualTo("Read unit 1"));

        assertThat(planB.getGoal()).isEqualTo("Organic chemistry");
        assertThat(planB.getSteps()).singleElement()
                .satisfies(step -> assertThat(step.getTitle()).isEqualTo("Review reactions"));
    }

    @Test
    void resavingSupersedesThePreviousActivePlan() {
        UUID user = seedUser();

        planApi.savePlan(new CurrentUser(user), "First goal", null, List.of(
                new PlanStepInput(1, LocalDate.now(), "Old step", PlanActivity.READ, List.of(), null)));
        planApi.savePlan(new CurrentUser(user), "Second goal", null, List.of(
                new PlanStepInput(1, LocalDate.now(), "New step", PlanActivity.QUIZ, List.of(), null)));

        LearningPlanDto active = planApi.getActivePlan(new CurrentUser(user)).orElseThrow();
        assertThat(active.getGoal()).isEqualTo("Second goal");
        assertThat(active.getSteps()).singleElement()
                .satisfies(step -> assertThat(step.getTitle()).isEqualTo("New step"));

        assertThat(plans.findByUserIdAndStatus(user, PlanStatus.ACTIVE)).hasSize(1);
        assertThat(plans.findByUserIdAndStatus(user, PlanStatus.SUPERSEDED)).hasSize(1);
    }

    @Test
    void noActivePlanReturnsEmpty() {
        UUID user = seedUser();
        assertThat(planApi.getActivePlan(new CurrentUser(user))).isEmpty();
    }

    @Test
    void completeStepMarksTheStepDoneAndTimestamped() {
        UUID user = seedUser();
        planApi.savePlan(new CurrentUser(user), "IELTS", null, List.of(
                new PlanStepInput(1, LocalDate.now(), "Read unit 1",
                        PlanActivity.READ, List.of(), null),
                new PlanStepInput(2, LocalDate.now().plusDays(1), "Quiz unit 1",
                        PlanActivity.QUIZ, List.of(), null)));

        LearningPlanDto updated = planApi.completeStep(new CurrentUser(user), 2).orElseThrow();
        assertThat(stepByDay(updated, 2).isDone()).isTrue();
        assertThat(stepByDay(updated, 1).isDone()).isFalse();

        LearningPlanDto reread = planApi.getActivePlan(new CurrentUser(user)).orElseThrow();
        assertThat(stepByDay(reread, 2).isDone()).isTrue();
        assertThat(stepByDay(reread, 1).isDone()).isFalse();

        transactionTemplate.executeWithoutResult(status -> {
            LearningPlan plan = plans.findByUserIdAndStatus(user, PlanStatus.ACTIVE).get(0);
            assertThat(stepEntityByDay(plan, 2).getCompletedAt()).isNotNull();
            assertThat(stepEntityByDay(plan, 1).getCompletedAt()).isNull();
        });
    }

    @Test
    void completeStepReturnsEmptyWhenNoActivePlan() {
        UUID user = seedUser();
        assertThat(planApi.completeStep(new CurrentUser(user), 1)).isEmpty();
    }

    @Test
    void activePlanResponseIsScopedToItsOwner() {
        UUID userA = seedUser();
        UUID userB = seedUser();
        planApi.savePlan(new CurrentUser(userA), "IELTS by July", LocalDate.now().plusDays(30), List.of(
                new PlanStepInput(1, LocalDate.now(), "Read unit 1", PlanActivity.READ, List.of(), "warm up"),
                new PlanStepInput(2, LocalDate.now().plusDays(1), "Quiz unit 1",
                        PlanActivity.QUIZ, List.of(), null)));
        planApi.savePlan(new CurrentUser(userB), "Organic chemistry", null, List.of(
                new PlanStepInput(1, LocalDate.now(), "Review reactions", PlanActivity.REVIEW, List.of(), null)));

        LearningPlanResponse responseA = planService.getActivePlanResponse(new CurrentUser(userA));
        LearningPlanResponse responseB = planService.getActivePlanResponse(new CurrentUser(userB));

        assertThat(responseA.getGoal()).isEqualTo("IELTS by July");
        assertThat(responseA.getDaysTotal()).isEqualTo(2);
        assertThat(responseA.getDaysCompleted()).isZero();
        assertThat(responseA.getSteps()).extracting(PlanStepResponse::getTitle)
                .containsExactly("Read unit 1", "Quiz unit 1");

        assertThat(responseB.getGoal()).isEqualTo("Organic chemistry");
        assertThat(responseB.getSteps()).extracting(PlanStepResponse::getTitle)
                .containsExactly("Review reactions");
    }

    @Test
    void activePlanResponseIsNullWhenNoPlan() {
        UUID user = seedUser();
        assertThat(planService.getActivePlanResponse(new CurrentUser(user))).isNull();
    }

    @Test
    void completeStepResponseMarksStepDone() {
        UUID user = seedUser();
        planApi.savePlan(new CurrentUser(user), "IELTS", null, List.of(
                new PlanStepInput(1, LocalDate.now(), "Read unit 1", PlanActivity.READ, List.of(), null),
                new PlanStepInput(2, LocalDate.now().plusDays(1), "Quiz unit 1",
                        PlanActivity.QUIZ, List.of(), null)));

        LearningPlanResponse updated = planService.completeStepResponse(new CurrentUser(user), 2);

        assertThat(updated.getDaysCompleted()).isEqualTo(1);
        assertThat(stepResponseByDay(updated, 2).isDone()).isTrue();
        assertThat(stepResponseByDay(updated, 2).getCompletedAt()).isNotNull();
        assertThat(stepResponseByDay(updated, 1).isDone()).isFalse();
    }

    @Test
    void completeStepResponseIsNullWhenNoActivePlan() {
        UUID user = seedUser();
        assertThat(planService.completeStepResponse(new CurrentUser(user), 1)).isNull();
    }

    @Test
    void materialUploadedEventMarksOnlyTheOwnersPlanForReplan() {
        UUID userA = seedUser();
        UUID userB = seedUser();
        planApi.savePlan(new CurrentUser(userA), "IELTS", null, List.of(
                new PlanStepInput(1, LocalDate.now(), "Read", PlanActivity.READ, List.of(), null)));
        planApi.savePlan(new CurrentUser(userB), "Chemistry", null, List.of(
                new PlanStepInput(1, LocalDate.now(), "Read", PlanActivity.READ, List.of(), null)));

        events.publishEvent(new MaterialUploadedEvent(UUID.randomUUID(), userA));

        assertThat(planApi.getActivePlan(new CurrentUser(userA)).orElseThrow().isReplanNeeded()).isTrue();
        assertThat(planApi.getActivePlan(new CurrentUser(userB)).orElseThrow().isReplanNeeded()).isFalse();
    }

    @Test
    void goalUpdatedEventMarksThePlanForReplan() {
        UUID user = seedUser();
        planApi.savePlan(new CurrentUser(user), "IELTS", null, List.of(
                new PlanStepInput(1, LocalDate.now(), "Read", PlanActivity.READ, List.of(), null)));

        events.publishEvent(new GoalUpdatedEvent(user));

        assertThat(planApi.getActivePlan(new CurrentUser(user)).orElseThrow().isReplanNeeded()).isTrue();
    }

    private static PlanStepDto stepByDay(LearningPlanDto plan, int day) {
        return plan.getSteps().stream()
                .filter(step -> step.getDayIndex() == day)
                .findFirst()
                .orElseThrow();
    }

    private static PlanStepResponse stepResponseByDay(LearningPlanResponse plan, int day) {
        return plan.getSteps().stream()
                .filter(step -> step.getDayIndex() == day)
                .findFirst()
                .orElseThrow();
    }

    private static PlanStep stepEntityByDay(LearningPlan plan, int day) {
        return plan.getSteps().stream()
                .filter(step -> step.getDayIndex() == day)
                .findFirst()
                .orElseThrow();
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
