package org.aiincubator.ilmai.agent.service;

import org.aiincubator.ilmai.common.CurrentUser;
import org.aiincubator.ilmai.rooms.RoomGoalDto;
import org.aiincubator.ilmai.rooms.RoomsApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GoalToolTest {

    private final UUID userA = UUID.randomUUID();
    private final UUID userB = UUID.randomUUID();

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getGoalReadsRoomGoalForSecurityContextUser() {
        RoomsApi roomsApi = mock(RoomsApi.class);
        LocalDate deadline = LocalDate.now().plusDays(10);
        when(roomsApi.findPersonalGoalForUser(userA)).thenReturn(Optional.of(roomGoal("Pass IELTS", deadline)));
        GoalTool tool = new GoalTool(roomsApi);

        authenticate(userA);
        GoalView view = tool.getGoal(new ToolContext(Map.of(AgentToolContext.CURRENT_USER_KEY, new CurrentUser(userA))));

        assertThat(view.isGoalSet()).isTrue();
        assertThat(view.getGoal()).isEqualTo("Pass IELTS");
        assertThat(view.getDeadline()).isEqualTo(deadline.toString());
        assertThat(view.getDaysUntilDeadline()).isEqualTo(10L);
        verify(roomsApi).findPersonalGoalForUser(userA);
    }

    @Test
    void getGoalReturnsUnsetWhenNoRoomGoal() {
        RoomsApi roomsApi = mock(RoomsApi.class);
        when(roomsApi.findPersonalGoalForUser(any())).thenReturn(Optional.empty());
        GoalTool tool = new GoalTool(roomsApi);

        authenticate(userA);
        GoalView view = tool.getGoal(new ToolContext(Map.of(AgentToolContext.CURRENT_USER_KEY, new CurrentUser(userA))));

        assertThat(view.isGoalSet()).isFalse();
        assertThat(view.getGoal()).isNull();
        assertThat(view.getDeadline()).isNull();
        assertThat(view.getDaysUntilDeadline()).isNull();
    }

    @Test
    void updateGoalWritesViaSecurityContextUserNotArguments() {
        RoomsApi roomsApi = mock(RoomsApi.class);
        LocalDate deadline = LocalDate.now().plusDays(30);
        when(roomsApi.applyGoalPatch(any(), any(), any(), any()))
                .thenReturn(Optional.of(roomGoal("Learn Spanish", deadline)));
        GoalTool tool = new GoalTool(roomsApi);

        authenticate(userB);
        GoalView view = tool.updateGoal("Learn Spanish", deadline.toString(), new ToolContext(Map.of(AgentToolContext.CURRENT_USER_KEY, new CurrentUser(userB))));

        assertThat(view.isGoalSet()).isTrue();
        assertThat(view.getGoal()).isEqualTo("Learn Spanish");
        verify(roomsApi).applyGoalPatch(userB, "Learn Spanish", deadline, null);
    }

    @Test
    void updateGoalWithBlankDeadlinePassesNullDeadline() {
        RoomsApi roomsApi = mock(RoomsApi.class);
        when(roomsApi.applyGoalPatch(any(), any(), any(), any()))
                .thenReturn(Optional.of(roomGoal("Finish course", null)));
        GoalTool tool = new GoalTool(roomsApi);

        authenticate(userA);
        GoalView view = tool.updateGoal("Finish course", "  ", new ToolContext(Map.of(AgentToolContext.CURRENT_USER_KEY, new CurrentUser(userA))));

        assertThat(view.getDeadline()).isNull();
        verify(roomsApi).applyGoalPatch(userA, "Finish course", null, null);
    }

    @Test
    void updateGoalRejectsInvalidDeadlineFormatWithoutWriting() {
        RoomsApi roomsApi = mock(RoomsApi.class);
        GoalTool tool = new GoalTool(roomsApi);

        authenticate(userA);
        assertThatThrownBy(() -> tool.updateGoal("Goal", "next-friday", new ToolContext(Map.of(AgentToolContext.CURRENT_USER_KEY, new CurrentUser(userA)))))
                .isInstanceOf(IllegalArgumentException.class);
        verify(roomsApi, never()).applyGoalPatch(any(), any(), any(), any());
    }

    @Test
    void toolsFailWhenSecurityContextIsAnonymous() {
        RoomsApi roomsApi = mock(RoomsApi.class);
        GoalTool tool = new GoalTool(roomsApi);

        assertThatThrownBy(() -> tool.getGoal(null)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> tool.updateGoal("Goal", null, null)).isInstanceOf(IllegalStateException.class);
        verify(roomsApi, never()).applyGoalPatch(any(), any(), any(), any());
    }

    private RoomGoalDto roomGoal(String goal, LocalDate targetDate) {
        return new RoomGoalDto(UUID.randomUUID(), goal, targetDate, null);
    }

    private void authenticate(UUID userId) {
        CurrentUser principal = new CurrentUser(userId);
        TestingAuthenticationToken auth = new TestingAuthenticationToken(principal, null);
        auth.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
