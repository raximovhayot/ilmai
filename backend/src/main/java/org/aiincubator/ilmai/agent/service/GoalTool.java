package org.aiincubator.ilmai.agent.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aiincubator.ilmai.rooms.RoomGoalDto;
import org.aiincubator.ilmai.rooms.RoomsApi;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class GoalTool {

    private final RoomsApi roomsApi;

    @Tool(description = "Read the current user's learning goal and optional deadline. Returns the goal text, the "
            + "deadline as an ISO date (YYYY-MM-DD), and how many days remain until it. Call this before "
            + "referencing or changing the user's goal. If no goal is set, goalSet is false.")
    public GoalView getGoal(ToolContext toolContext) {
        UUID userId = AgentToolContext.requireUserId(toolContext);
        return roomsApi.findPersonalGoalForUser(userId).map(this::toView).orElseGet(GoalTool::emptyGoal);
    }

    @Tool(description = "Set or update the current user's learning goal and optional deadline. Call this when the "
            + "user states or changes what they want to achieve, or by when (e.g. 'I want to pass IELTS by July'). "
            + "Provide only the fields the user mentioned; omit a field to leave any existing value unchanged. The "
            + "deadline must be an ISO date (YYYY-MM-DD) and cannot be in the past.")
    public GoalView updateGoal(
            @ToolParam(required = false, description = "The learning goal in the user's own words, e.g. 'Pass IELTS'.")
            String goal,
            @ToolParam(required = false, description = "Optional deadline as an ISO date (YYYY-MM-DD).")
            String deadline,
            ToolContext toolContext) {
        UUID userId = AgentToolContext.requireUserId(toolContext);
        LocalDate targetDate = parseDeadline(deadline);
        GoalView view = roomsApi.applyGoalPatch(userId, goal, targetDate, null)
                .map(this::toView).orElseGet(GoalTool::emptyGoal);
        log.debug("agent.updateGoal user={} goalSet={} deadline={}", userId, view.isGoalSet(), targetDate);
        return view;
    }

    private LocalDate parseDeadline(String deadline) {
        if (deadline == null || deadline.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(deadline.trim());
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("deadline must be an ISO date in the form YYYY-MM-DD");
        }
    }

    private GoalView toView(RoomGoalDto roomGoal) {
        String goal = roomGoal.getGoal();
        LocalDate targetDate = roomGoal.getTargetDate();
        String deadline = targetDate != null ? targetDate.toString() : null;
        Long daysUntilDeadline = targetDate != null
                ? ChronoUnit.DAYS.between(LocalDate.now(), targetDate)
                : null;
        return new GoalView(goal != null, goal, deadline, daysUntilDeadline);
    }

    private static GoalView emptyGoal() {
        return new GoalView(false, null, null, null);
    }
}
