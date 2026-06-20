package org.aiincubator.ilmai.agent.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aiincubator.ilmai.common.CurrentUser;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class BuildPlanTool {

    private final PlanBuilder planBuilder;
    private final PlanViewFactory planViewFactory;

    @Tool(description = "Build a day-by-day study plan for the current user from their own goal, uploaded materials, "
            + "and known weak concepts, then save it as their active plan (replacing any previous one). Call this when "
            + "the user asks for a plan, a schedule, or how to prepare. Returns the saved plan. If hasPlan is false the "
            + "plan could not be built (usually because no materials are ready) - ask the user to upload material first.")
    public PlanView buildPlan(
            @ToolParam(required = false, description = "How many days the plan should span. Defaults to the days left "
                    + "until the deadline, or 14 when there is no deadline.")
            Integer days,
            @ToolParam(required = false, description = "Language to write the plan in: 'en', 'ru', or 'uz'. Use the "
                    + "language the user is writing in.")
            String language,
            ToolContext toolContext) {
        CurrentUser currentUser = AgentToolContext.requireCurrentUser(toolContext);
        UUID userId = currentUser.getUserId();
        return planBuilder.build(userId, days, language)
                .map(saved -> planViewFactory.toView(userId, saved, saved.getSteps()))
                .orElseGet(BuildPlanTool::emptyView);
    }

    private static PlanView emptyView() {
        return new PlanView(false, null, null, List.of(), false);
    }
}
