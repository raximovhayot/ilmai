package org.aiincubator.ilmai.agent.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aiincubator.ilmai.common.CurrentUser;
import org.aiincubator.ilmai.plan.LearningPlanDto;
import org.aiincubator.ilmai.plan.PlanApi;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class CompleteStepTool {

    private final PlanApi planApi;
    private final PlanViewFactory planViewFactory;

    @Tool(description = "Mark one step of the current user's active study plan as done. Call this when the user says "
            + "they have finished or completed a plan step or today's task. Identify the step by its 1-based day "
            + "number as shown in the plan (the 'day' field). Returns the updated plan so you can confirm progress "
            + "and see what is next; if hasPlan is false the user has no active plan to update - offer to build one "
            + "with buildPlan.")
    public PlanView completeStep(
            @ToolParam(description = "The 1-based day number of the plan step the user completed, as shown in the "
                    + "plan's steps.")
            int day,
            ToolContext toolContext) {
        CurrentUser currentUser = AgentToolContext.requireCurrentUser(toolContext);
        LearningPlanDto plan = planApi.completeStep(currentUser, day).orElse(null);
        if (plan == null) {
            return new PlanView(false, null, null, List.of(), false);
        }
        log.debug("agent.completeStep user={} day={}", currentUser.getUserId(), day);
        return planViewFactory.toView(currentUser.getUserId(), plan, plan.getSteps());
    }
}
