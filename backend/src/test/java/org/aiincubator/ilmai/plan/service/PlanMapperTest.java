package org.aiincubator.ilmai.plan.service;

import org.aiincubator.ilmai.materials.MaterialDto;
import org.aiincubator.ilmai.materials.MaterialsApi;
import org.aiincubator.ilmai.plan.PlanActivity;
import org.aiincubator.ilmai.plan.PlanStatus;
import org.aiincubator.ilmai.plan.domain.LearningPlan;
import org.aiincubator.ilmai.plan.domain.PlanStep;
import org.aiincubator.ilmai.plan.payload.LearningPlanResponse;
import org.aiincubator.ilmai.plan.payload.PlanMaterialRef;
import org.aiincubator.ilmai.plan.payload.PlanStepResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlanMapperTest {

    private MaterialsApi materialsApi;
    private PlanMapper mapper;

    @BeforeEach
    void setUp() {
        materialsApi = mock(MaterialsApi.class);
        mapper = new PlanMapperImpl();
        mapper.setMaterialsApi(materialsApi);
        when(materialsApi.findById(any())).thenReturn(Optional.empty());
    }

    @Test
    void mapsPlanFieldsAndDayCounts() {
        LearningPlan plan = new LearningPlan();
        plan.setId(UUID.randomUUID());
        plan.setUserId(UUID.randomUUID());
        plan.setGoal("IELTS by July");
        plan.setTargetDate(LocalDate.of(2026, 7, 1));
        plan.setStatus(PlanStatus.ACTIVE);
        plan.setReplanNeeded(true);
        plan.setSteps(List.of(
                step(1, LocalDate.of(2026, 6, 2), "Read unit 1", PlanActivity.READ, "warm up", false, null),
                step(2, LocalDate.of(2026, 6, 3), "Quiz unit 1", PlanActivity.QUIZ, null, true,
                        OffsetDateTime.now())));

        LearningPlanResponse response = mapper.toResponse(plan);

        assertThat(response.getGoal()).isEqualTo("IELTS by July");
        assertThat(response.getTargetDate()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(response.getStatus()).isEqualTo(PlanStatus.ACTIVE);
        assertThat(response.isReplanNeeded()).isTrue();
        assertThat(response.getDaysTotal()).isEqualTo(2);
        assertThat(response.getDaysCompleted()).isEqualTo(1);
        assertThat(response.getSteps()).hasSize(2);

        PlanStepResponse first = response.getSteps().get(0);
        assertThat(first.getDayIndex()).isEqualTo(1);
        assertThat(first.getTitle()).isEqualTo("Read unit 1");
        assertThat(first.getActivity()).isEqualTo(PlanActivity.READ);
        assertThat(first.getNote()).isEqualTo("warm up");
        assertThat(first.isDone()).isFalse();
        assertThat(first.getCompletedAt()).isNull();

        PlanStepResponse second = response.getSteps().get(1);
        assertThat(second.isDone()).isTrue();
        assertThat(second.getCompletedAt()).isNotNull();
    }

    @Test
    void resolvesMaterialTitlesAndSkipsMissingMaterials() {
        UUID present = UUID.randomUUID();
        UUID missing = UUID.randomUUID();
        UUID topicId = UUID.randomUUID();
        when(materialsApi.findById(present)).thenReturn(Optional.of(
                new MaterialDto(present, topicId, null, "Algebra basics", null, null, null, 0, null, null)));

        PlanStep planStep = step(1, LocalDate.now(), "Read", PlanActivity.READ, null, false, null);
        planStep.setMaterialIds(List.of(present, missing));
        LearningPlan plan = new LearningPlan();
        plan.setStatus(PlanStatus.ACTIVE);
        plan.setSteps(List.of(planStep));

        PlanStepResponse mapped = mapper.toResponse(plan).getSteps().get(0);

        assertThat(mapped.getMaterials())
                .singleElement()
                .satisfies(ref -> {
                    assertThat(ref.getId()).isEqualTo(present);
                    assertThat(ref.getTitle()).isEqualTo("Algebra basics");
                    assertThat(ref.getTopicId()).isEqualTo(topicId);
                });
    }

    @Test
    void nullMaterialIdsYieldEmptyMaterialList() {
        PlanStep planStep = step(1, LocalDate.now(), "Read", PlanActivity.READ, null, false, null);
        planStep.setMaterialIds(null);

        PlanStepResponse mapped = mapper.toResponse(planStep);

        assertThat(mapped.getMaterials()).extracting(PlanMaterialRef::getId).isEmpty();
    }

    private static PlanStep step(int dayIndex, LocalDate scheduledDate, String title, PlanActivity activity,
                                 String note, boolean done, OffsetDateTime completedAt) {
        PlanStep step = new PlanStep();
        step.setDayIndex(dayIndex);
        step.setScheduledDate(scheduledDate);
        step.setTitle(title);
        step.setActivity(activity);
        step.setNote(note);
        step.setDone(done);
        step.setCompletedAt(completedAt);
        return step;
    }
}
