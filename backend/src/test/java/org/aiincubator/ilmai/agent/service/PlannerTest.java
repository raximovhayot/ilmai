package org.aiincubator.ilmai.agent.service;

import org.aiincubator.ilmai.plan.PlanActivity;
import org.aiincubator.ilmai.plan.PlanStepInput;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PlannerTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    void parseStepsResolvesMaterialNumbersToOwnedIdsAndDropsHallucinatedOnes() {
        UUID m1 = UUID.randomUUID();
        UUID m2 = UUID.randomUUID();
        List<PlannerMaterial> owned = List.of(
                new PlannerMaterial(1, m1, "Algebra"),
                new PlannerMaterial(2, m2, "Calculus"));
        String json = "["
                + "{\"day\":1,\"title\":\"Read algebra\",\"activity\":\"READ\",\"materials\":[1],\"note\":\"start\"},"
                + "{\"day\":2,\"title\":\"Quiz calculus\",\"activity\":\"QUIZ\",\"materials\":[2,9,1]},"
                + "{\"day\":3,\"title\":\"Bogus\",\"activity\":\"REVIEW\",\"materials\":[7,42]}"
                + "]";

        List<PlanStepInput> steps = Planner.parseSteps(jsonMapper, json, owned, LocalDate.of(2026, 6, 1));

        assertThat(steps).hasSize(3);
        assertThat(steps).allSatisfy(step ->
                assertThat(step.getMaterialIds()).allMatch(id -> id.equals(m1) || id.equals(m2)));
        assertThat(steps.get(0).getMaterialIds()).containsExactly(m1);
        assertThat(steps.get(0).getActivity()).isEqualTo(PlanActivity.READ);
        assertThat(steps.get(0).getScheduledDate()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(steps.get(1).getMaterialIds()).containsExactly(m2, m1);
        assertThat(steps.get(1).getActivity()).isEqualTo(PlanActivity.QUIZ);
        assertThat(steps.get(1).getScheduledDate()).isEqualTo(LocalDate.of(2026, 6, 2));
        assertThat(steps.get(2).getMaterialIds()).isEmpty();
    }

    @Test
    void parseStepsHandlesProseWrappedJsonBlankTitlesAndBadActivity() {
        UUID m1 = UUID.randomUUID();
        List<PlannerMaterial> owned = List.of(new PlannerMaterial(1, m1, "Notes"));
        String response = "Sure! Here is your plan:\n"
                + "[{\"day\":1,\"title\":\"   \",\"materials\":[1]},"
                + "{\"day\":\"2\",\"title\":\"Review notes\",\"activity\":\"banana\",\"materials\":[1]}]\nEnjoy.";

        List<PlanStepInput> steps = Planner.parseSteps(jsonMapper, response, owned, null);

        assertThat(steps).hasSize(1);
        assertThat(steps.get(0).getTitle()).isEqualTo("Review notes");
        assertThat(steps.get(0).getActivity()).isEqualTo(PlanActivity.READ);
        assertThat(steps.get(0).getDayIndex()).isEqualTo(2);
        assertThat(steps.get(0).getScheduledDate()).isNull();
        assertThat(steps.get(0).getMaterialIds()).containsExactly(m1);
    }

    @Test
    void parseStepsReturnsEmptyOnNonJsonOrBlank() {
        assertThat(Planner.parseSteps(jsonMapper, "no json here", List.of(), null)).isEmpty();
        assertThat(Planner.parseSteps(jsonMapper, "", List.of(), null)).isEmpty();
        assertThat(Planner.parseSteps(jsonMapper, null, List.of(), null)).isEmpty();
    }
}
