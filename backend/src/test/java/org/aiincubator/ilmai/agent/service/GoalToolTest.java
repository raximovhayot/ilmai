package org.aiincubator.ilmai.agent.service;

import org.aiincubator.ilmai.common.CurrentUser;
import org.aiincubator.ilmai.profiles.ProfileDto;
import org.aiincubator.ilmai.profiles.ProfilesApi;
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
    void getGoalReadsProfileForSecurityContextUser() {
        ProfilesApi profilesApi = mock(ProfilesApi.class);
        LocalDate deadline = LocalDate.now().plusDays(10);
        when(profilesApi.find(userA)).thenReturn(Optional.of(profile(userA, "Pass IELTS", deadline)));
        GoalTool tool = new GoalTool(profilesApi);

        authenticate(userA);
        GoalView view = tool.getGoal(new ToolContext(Map.of(AgentToolContext.CURRENT_USER_KEY, new CurrentUser(userA))));

        assertThat(view.isGoalSet()).isTrue();
        assertThat(view.getGoal()).isEqualTo("Pass IELTS");
        assertThat(view.getDeadline()).isEqualTo(deadline.toString());
        assertThat(view.getDaysUntilDeadline()).isEqualTo(10L);
        verify(profilesApi).find(userA);
    }

    @Test
    void getGoalReturnsUnsetWhenNoProfile() {
        ProfilesApi profilesApi = mock(ProfilesApi.class);
        when(profilesApi.find(any())).thenReturn(Optional.empty());
        GoalTool tool = new GoalTool(profilesApi);

        authenticate(userA);
        GoalView view = tool.getGoal(new ToolContext(Map.of(AgentToolContext.CURRENT_USER_KEY, new CurrentUser(userA))));

        assertThat(view.isGoalSet()).isFalse();
        assertThat(view.getGoal()).isNull();
        assertThat(view.getDeadline()).isNull();
        assertThat(view.getDaysUntilDeadline()).isNull();
    }

    @Test
    void updateGoalWritesViaSecurityContextUserNotArguments() {
        ProfilesApi profilesApi = mock(ProfilesApi.class);
        LocalDate deadline = LocalDate.now().plusDays(30);
        when(profilesApi.updateGoal(any(), any(), any()))
                .thenReturn(profile(userB, "Learn Spanish", deadline));
        GoalTool tool = new GoalTool(profilesApi);

        authenticate(userB);
        GoalView view = tool.updateGoal("Learn Spanish", deadline.toString(), new ToolContext(Map.of(AgentToolContext.CURRENT_USER_KEY, new CurrentUser(userB))));

        assertThat(view.isGoalSet()).isTrue();
        assertThat(view.getGoal()).isEqualTo("Learn Spanish");
        verify(profilesApi).updateGoal(userB, "Learn Spanish", deadline);
    }

    @Test
    void updateGoalWithBlankDeadlinePassesNullDeadline() {
        ProfilesApi profilesApi = mock(ProfilesApi.class);
        when(profilesApi.updateGoal(any(), any(), any()))
                .thenReturn(profile(userA, "Finish course", null));
        GoalTool tool = new GoalTool(profilesApi);

        authenticate(userA);
        GoalView view = tool.updateGoal("Finish course", "  ", new ToolContext(Map.of(AgentToolContext.CURRENT_USER_KEY, new CurrentUser(userA))));

        assertThat(view.getDeadline()).isNull();
        verify(profilesApi).updateGoal(userA, "Finish course", null);
    }

    @Test
    void updateGoalRejectsInvalidDeadlineFormatWithoutWriting() {
        ProfilesApi profilesApi = mock(ProfilesApi.class);
        GoalTool tool = new GoalTool(profilesApi);

        authenticate(userA);
        assertThatThrownBy(() -> tool.updateGoal("Goal", "next-friday", new ToolContext(Map.of(AgentToolContext.CURRENT_USER_KEY, new CurrentUser(userA)))))
                .isInstanceOf(IllegalArgumentException.class);
        verify(profilesApi, never()).updateGoal(any(), any(), any());
    }

    @Test
    void toolsFailWhenSecurityContextIsAnonymous() {
        ProfilesApi profilesApi = mock(ProfilesApi.class);
        GoalTool tool = new GoalTool(profilesApi);

        assertThatThrownBy(() -> tool.getGoal(null)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> tool.updateGoal("Goal", null, null)).isInstanceOf(IllegalStateException.class);
        verify(profilesApi, never()).updateGoal(any(), any(), any());
    }

    private ProfileDto profile(UUID userId, String goal, LocalDate targetDate) {
        return new ProfileDto(userId, null, null, goal, targetDate, null, null, 0, 0, 0, null);
    }

    private void authenticate(UUID userId) {
        CurrentUser principal = new CurrentUser(userId);
        TestingAuthenticationToken auth = new TestingAuthenticationToken(principal, null);
        auth.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
