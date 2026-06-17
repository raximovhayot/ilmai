package org.aiincubator.ilmai.agent.service;

import lombok.extern.slf4j.Slf4j;
import org.aiincubator.ilmai.common.CurrentUser;
import org.aiincubator.ilmai.profiles.ProfileDto;
import org.aiincubator.ilmai.profiles.ProfilesApi;
import org.aiincubator.ilmai.streaks.StreakDto;
import org.aiincubator.ilmai.streaks.StreaksApi;
import org.aiincubator.ilmai.agent.UserFactDto;
import org.aiincubator.ilmai.agent.UserMemoryApi;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.core.Ordered;
import reactor.core.publisher.Flux;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
public class UserMemoryAdvisor implements CallAdvisor, StreamAdvisor {

    public static final String CURRENT_USER_PARAM = "agent.current_user";

    private static final int DEFAULT_ORDER = Ordered.HIGHEST_PRECEDENCE + 500;
    private static final int DEFAULT_MAX_FACTS = 20;
    private static final int DEFAULT_MAX_CHARS = 1200;

    private static final String BLOCK_HEADER = "[user context]";
    private static final String GOAL_PREFIX = "Learning goal: ";
    private static final String STREAK_PREFIX = "Current study streak: ";
    private static final String FACTS_HEADER = "Known facts about the user:";

    private final UserMemoryApi userMemoryApi;
    private final ProfilesApi profilesApi;
    private final StreaksApi streaksApi;
    private final int order;
    private final int maxFacts;
    private final int maxChars;

    public UserMemoryAdvisor(UserMemoryApi userMemoryApi, ProfilesApi profilesApi, StreaksApi streaksApi) {
        this(userMemoryApi, profilesApi, streaksApi, DEFAULT_ORDER, DEFAULT_MAX_FACTS, DEFAULT_MAX_CHARS);
    }

    public UserMemoryAdvisor(UserMemoryApi userMemoryApi, ProfilesApi profilesApi, StreaksApi streaksApi,
                             int order, int maxFacts, int maxChars) {
        this.userMemoryApi = userMemoryApi;
        this.profilesApi = profilesApi;
        this.streaksApi = streaksApi;
        this.order = order;
        this.maxFacts = maxFacts;
        this.maxChars = maxChars;
    }

    @Override
    public int getOrder() {
        return order;
    }

    @Override
    public String getName() {
        return getClass().getSimpleName();
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        return chain.nextCall(injectUserContext(request));
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        return chain.nextStream(injectUserContext(request));
    }

    private ChatClientRequest injectUserContext(ChatClientRequest request) {
        CurrentUser currentUser = resolveUser(request);
        if (currentUser == null) {
            return request;
        }
        String block = buildContextBlock(currentUser);
        if (block.isEmpty()) {
            return request;
        }
        Prompt prompt = request.prompt();
        List<Message> messages = new ArrayList<>(prompt.getInstructions());
        boolean injected = false;
        for (int i = 0; i < messages.size(); i++) {
            if (messages.get(i) instanceof SystemMessage system) {
                messages.set(i, new SystemMessage(appendBlock(system.getText(), block)));
                injected = true;
                break;
            }
        }
        if (!injected) {
            messages.add(0, new SystemMessage(block));
        }
        return request.mutate().prompt(new Prompt(messages)).build();
    }

    private CurrentUser resolveUser(ChatClientRequest request) {
        Object value = request.context().get(CURRENT_USER_PARAM);
        return value instanceof CurrentUser currentUser ? currentUser : null;
    }

    private String appendBlock(String systemText, String block) {
        if (systemText == null || systemText.isBlank()) {
            return block;
        }
        return systemText + "\n\n" + block;
    }

    private String buildContextBlock(CurrentUser currentUser) {
        List<String> headerLines = new ArrayList<>();
        Optional<ProfileDto> profile = safeProfile(currentUser);
        profile.map(this::goalLine).ifPresent(headerLines::add);
        streakLine(currentUser).ifPresent(headerLines::add);

        List<String> factLines = new ArrayList<>();
        for (UserFactDto fact : userMemoryApi.recentFacts(currentUser, maxFacts)) {
            if (fact.getContent() != null && !fact.getContent().isBlank()) {
                factLines.add("- " + fact.getContent().trim());
            }
        }
        return assemble(headerLines, factLines);
    }

    private String assemble(List<String> headerLines, List<String> factLines) {
        StringBuilder block = new StringBuilder(BLOCK_HEADER);
        for (String line : headerLines) {
            if (block.length() + 1 + line.length() > maxChars) {
                break;
            }
            block.append('\n').append(line);
        }
        if (!factLines.isEmpty() && block.length() + 1 + FACTS_HEADER.length() <= maxChars) {
            block.append('\n').append(FACTS_HEADER);
            for (String line : factLines) {
                if (block.length() + 1 + line.length() > maxChars) {
                    break;
                }
                block.append('\n').append(line);
            }
        }
        if (block.length() == BLOCK_HEADER.length()) {
            return "";
        }
        return block.toString();
    }

    private Optional<ProfileDto> safeProfile(CurrentUser currentUser) {
        try {
            return profilesApi.find(currentUser.getUserId());
        } catch (RuntimeException ex) {
            log.debug("user-memory: profile lookup failed for {}: {}", currentUser.getUserId(), ex.toString());
            return Optional.empty();
        }
    }

    private String goalLine(ProfileDto profile) {
        String goal = profile.getGoal();
        if (goal == null || goal.isBlank()) {
            return null;
        }
        LocalDate target = profile.getTargetDate();
        if (target != null) {
            return GOAL_PREFIX + goal.trim() + " (target date: " + target + ")";
        }
        return GOAL_PREFIX + goal.trim();
    }

    private Optional<String> streakLine(CurrentUser currentUser) {
        int current = safeStreakCurrent(currentUser);
        if (current <= 0) {
            return Optional.empty();
        }
        return Optional.of(STREAK_PREFIX + current + (current == 1 ? " day" : " days"));
    }

    private int safeStreakCurrent(CurrentUser currentUser) {
        try {
            StreakDto streak = streaksApi.getStreak(currentUser.getUserId());
            return streak == null ? 0 : streak.getStreakCurrent();
        } catch (RuntimeException ex) {
            log.debug("user-memory: streak lookup failed for {}: {}", currentUser.getUserId(), ex.toString());
            return 0;
        }
    }
}
