package org.aiincubator.ilmai.agent.service;

import org.aiincubator.ilmai.common.CurrentUser;
import org.aiincubator.ilmai.plan.LearningPlanDto;
import org.aiincubator.ilmai.plan.PlanStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ToolContext;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BuildPlanToolTest {

    private final UUID userA = UUID.randomUUID();

    @Mock PlanBuilder planBuilder;
    @Mock PlanViewFactory planViewFactory;

    @InjectMocks BuildPlanTool tool;

    @Test
    void buildPlanReturnsNotReadyWhenBuilderProducesNothing() {
        when(planBuilder.build(eq(userA), isNull(), eq("en"))).thenReturn(Optional.empty());

        PlanView view = tool.buildPlan(null, "en", toolContext());

        assertThat(view.isHasPlan()).isFalse();
        assertThat(view.getSteps()).isEmpty();
        verifyNoInteractions(planViewFactory);
    }

    @Test
    void buildPlanRendersViewFromSavedPlan() {
        LearningPlanDto saved = new LearningPlanDto(UUID.randomUUID(), "IELTS", null,
                PlanStatus.ACTIVE, OffsetDateTime.now(), List.of(), false);
        when(planBuilder.build(eq(userA), eq(7), eq("en"))).thenReturn(Optional.of(saved));
        PlanView expected = new PlanView(true, "IELTS", null, List.of(), false);
        when(planViewFactory.toView(eq(userA), eq(saved), anyList())).thenReturn(expected);

        PlanView view = tool.buildPlan(7, "en", toolContext());

        assertThat(view).isSameAs(expected);
    }

    private ToolContext toolContext() {
        return new ToolContext(Map.of(AgentToolContext.CURRENT_USER_KEY, new CurrentUser(userA)));
    }
}
