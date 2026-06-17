package org.aiincubator.ilmai.agent.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aiincubator.ilmai.common.CurrentUser;
import org.aiincubator.ilmai.plan.LearningPlanDto;
import org.aiincubator.ilmai.plan.PlanApi;
import org.aiincubator.ilmai.plan.PlanStepDto;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class GetPlanTool {

    private final PlanApi planApi;
    private final PlanViewFactory planViewFactory;

    @Tool(description = "Return the current user's active study plan (goal, deadline, and the full list of day-by-day "
            + "steps with their materials and completion state). Call this when the user asks to see their plan or "
            + "schedule. If hasPlan is false the user has no active plan yet - offer to build one with buildPlan.")
    public PlanView getPlan(ToolContext toolContext) {
        CurrentUser currentUser = AgentToolContext.requireCurrentUser(toolContext);
        LearningPlanDto plan = planApi.getActivePlan(currentUser).orElse(null);
        if (plan == null) {
            return emptyView();
        }
        return planViewFactory.toView(currentUser.getUserId(), plan, plan.getSteps());
    }

    @Tool(description = "Return only the study step(s) the current user should do today from their active plan. Call "
            + "this when the user asks what to study today or what is next. If hasPlan is false the user has no active "
            + "plan yet - offer to build one with buildPlan. An empty step list means nothing is scheduled for today.")
    public PlanView getTodaysTask(ToolContext toolContext) {
        CurrentUser currentUser = AgentToolContext.requireCurrentUser(toolContext);
        LearningPlanDto plan = planApi.getActivePlan(currentUser).orElse(null);
        if (plan == null) {
            return emptyView();
        }
        return planViewFactory.toView(currentUser.getUserId(), plan, todaysSteps(plan.getSteps()));
    }

    private List<PlanStepDto> todaysSteps(List<PlanStepDto> steps) {
        LocalDate today = LocalDate.now();
        List<PlanStepDto> dueToday = new ArrayList<>();
        for (PlanStepDto step : steps) {
            if (today.equals(step.getScheduledDate())) {
                dueToday.add(step);
            }
        }
        if (!dueToday.isEmpty()) {
            return dueToday;
        }
        for (PlanStepDto step : steps) {
            if (!step.isDone()) {
                return List.of(step);
            }
        }
        return List.of();
    }

    private PlanView emptyView() {
        return new PlanView(false, null, null, List.of(), false);
    }
}
