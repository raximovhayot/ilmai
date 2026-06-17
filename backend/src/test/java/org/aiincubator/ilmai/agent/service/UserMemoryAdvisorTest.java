package org.aiincubator.ilmai.agent.service;

import org.aiincubator.ilmai.common.CurrentUser;
import org.aiincubator.ilmai.profiles.ProfileDto;
import org.aiincubator.ilmai.profiles.ProfilesApi;
import org.aiincubator.ilmai.streaks.StreakDto;
import org.aiincubator.ilmai.streaks.StreaksApi;
import org.aiincubator.ilmai.agent.UserFactDto;
import org.aiincubator.ilmai.agent.UserMemoryApi;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class UserMemoryAdvisorTest {

    private final UUID userId = UUID.randomUUID();
    private final CurrentUser currentUser = new CurrentUser(userId);

    @Test
    void injectsFactsIntoSystemMessage() {
        UserMemoryApi userMemoryApi = mock(UserMemoryApi.class);
        ProfilesApi profilesApi = mock(ProfilesApi.class);
        StreaksApi streaksApi = mock(StreaksApi.class);
        when(profilesApi.find(any())).thenReturn(Optional.empty());
        when(userMemoryApi.recentFacts(any(), anyInt())).thenReturn(List.of(
                fact("prefers worked examples"),
                fact("is preparing for IELTS")));
        UserMemoryAdvisor advisor = new UserMemoryAdvisor(userMemoryApi, profilesApi, streaksApi);

        ScriptedCallAdvisorChain chain = new ScriptedCallAdvisorChain(List.of("ok"));
        advisor.adviseCall(request("You are the Coach.", currentUser), chain);

        String system = systemText(chain.requests().get(0));
        assertThat(system).contains("You are the Coach.")
                .contains("[user context]")
                .contains("Known facts about the user:")
                .contains("- prefers worked examples")
                .contains("- is preparing for IELTS");
    }

    @Test
    void injectsGoalFromProfileAndStreakFromStreaks() {
        UserMemoryApi userMemoryApi = mock(UserMemoryApi.class);
        ProfilesApi profilesApi = mock(ProfilesApi.class);
        StreaksApi streaksApi = mock(StreaksApi.class);
        when(userMemoryApi.recentFacts(any(), anyInt())).thenReturn(List.of());
        when(profilesApi.find(any())).thenReturn(Optional.of(new ProfileDto(
                userId, null, null, "Pass IELTS", LocalDate.parse("2026-07-01"),
                null, null, 0, 0, 0, null)));
        when(streaksApi.getStreak(userId)).thenReturn(
                new StreakDto(userId, 4, 9, LocalDate.parse("2026-05-31"), null));
        UserMemoryAdvisor advisor = new UserMemoryAdvisor(userMemoryApi, profilesApi, streaksApi);

        ScriptedCallAdvisorChain chain = new ScriptedCallAdvisorChain(List.of("ok"));
        advisor.adviseCall(request("base", currentUser), chain);

        String system = systemText(chain.requests().get(0));
        assertThat(system).contains("Learning goal: Pass IELTS (target date: 2026-07-01)")
                .contains("Current study streak: 4 days");
    }

    @Test
    void passesThroughWhenNoCurrentUser() {
        UserMemoryApi userMemoryApi = mock(UserMemoryApi.class);
        ProfilesApi profilesApi = mock(ProfilesApi.class);
        StreaksApi streaksApi = mock(StreaksApi.class);
        UserMemoryAdvisor advisor = new UserMemoryAdvisor(userMemoryApi, profilesApi, streaksApi);

        ScriptedCallAdvisorChain chain = new ScriptedCallAdvisorChain(List.of("ok"));
        advisor.adviseCall(request("base only", null), chain);

        assertThat(systemText(chain.requests().get(0))).isEqualTo("base only");
        verifyNoInteractions(userMemoryApi, profilesApi, streaksApi);
    }

    @Test
    void passesThroughWhenNothingToInject() {
        UserMemoryApi userMemoryApi = mock(UserMemoryApi.class);
        ProfilesApi profilesApi = mock(ProfilesApi.class);
        StreaksApi streaksApi = mock(StreaksApi.class);
        when(profilesApi.find(any())).thenReturn(Optional.empty());
        when(userMemoryApi.recentFacts(any(), anyInt())).thenReturn(List.of());
        UserMemoryAdvisor advisor = new UserMemoryAdvisor(userMemoryApi, profilesApi, streaksApi);

        ScriptedCallAdvisorChain chain = new ScriptedCallAdvisorChain(List.of("ok"));
        advisor.adviseCall(request("base only", currentUser), chain);

        assertThat(systemText(chain.requests().get(0))).isEqualTo("base only");
    }

    @Test
    void capsBlockAtMaxCharsKeepingNewestFacts() {
        UserMemoryApi userMemoryApi = mock(UserMemoryApi.class);
        ProfilesApi profilesApi = mock(ProfilesApi.class);
        StreaksApi streaksApi = mock(StreaksApi.class);
        when(profilesApi.find(any())).thenReturn(Optional.empty());
        when(userMemoryApi.recentFacts(any(), anyInt())).thenReturn(List.of(
                fact("newest fact kept"),
                fact("older fact dropped due to the tight character budget")));
        UserMemoryAdvisor advisor = new UserMemoryAdvisor(userMemoryApi, profilesApi, streaksApi, 0, 20, 70);

        ScriptedCallAdvisorChain chain = new ScriptedCallAdvisorChain(List.of("ok"));
        advisor.adviseCall(request("", currentUser), chain);

        String system = systemText(chain.requests().get(0));
        assertThat(system).contains("- newest fact kept");
        assertThat(system).doesNotContain("older fact dropped");
        assertThat(system.length()).isLessThanOrEqualTo(70);
    }

    private UserFactDto fact(String content) {
        return new UserFactDto(UUID.randomUUID(), content, OffsetDateTime.now());
    }

    private ChatClientRequest request(String systemText, CurrentUser user) {
        Map<String, Object> context = new HashMap<>();
        if (user != null) {
            context.put(UserMemoryAdvisor.CURRENT_USER_PARAM, user);
        }
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemText));
        messages.add(new UserMessage("hello"));
        return ChatClientRequest.builder()
                .prompt(new Prompt(messages))
                .context(context)
                .build();
    }

    private String systemText(ChatClientRequest request) {
        return request.prompt().getInstructions().stream()
                .filter(message -> message instanceof SystemMessage)
                .map(Message::getText)
                .findFirst()
                .orElse("");
    }
}
