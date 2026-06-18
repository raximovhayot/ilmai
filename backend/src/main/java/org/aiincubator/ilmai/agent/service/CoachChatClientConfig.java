package org.aiincubator.ilmai.agent.service;

import lombok.extern.slf4j.Slf4j;
import org.aiincubator.ilmai.agent.domain.ChatMemorySummaryRepository;
import org.aiincubator.ilmai.ai.IlmaiChatClientFactory;
import org.aiincubator.ilmai.common.i18n.MessageService;
import org.aiincubator.ilmai.profiles.ProfilesApi;
import org.aiincubator.ilmai.streaks.StreaksApi;
import org.aiincubator.ilmai.agent.UserMemoryApi;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.ToolCallAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.concurrent.DelegatingSecurityContextExecutor;
import uz.uzinfoweb.uimessagestream.spring.UiMessageStreamEmitter;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Configuration
@Slf4j
public class CoachChatClientConfig {

    public static final String COACH_CHAT_CLIENT = "coachChatClient";
    public static final String SUMMARY_CHAT_CLIENT = "summaryChatClient";
    public static final String FACTS_CHAT_CLIENT = "factsChatClient";
    public static final String GAPS_NARRATOR_CHAT_CLIENT = "gapsNarratorChatClient";
    public static final String PLANNER_CHAT_CLIENT = "plannerChatClient";
    public static final String DIGEST_NARRATOR_CHAT_CLIENT = "digestNarratorChatClient";
    public static final String COACH_STREAM_EXECUTOR = "coachStreamExecutor";

    static final int COACH_MEMORY_MAX_MESSAGES = 20;

    @Bean(name = COACH_STREAM_EXECUTOR)
    Executor coachStreamExecutor() {
        return new DelegatingSecurityContextExecutor(Executors.newVirtualThreadPerTaskExecutor());
    }

    @Bean
    UiMessageStreamEmitter uiMessageStreamEmitter() {
        return new UiMessageStreamEmitter();
    }

    @Bean
    GroundingGuardAdvisor groundingGuardAdvisor() {
        return new GroundingGuardAdvisor();
    }

    @Bean
    CitationGuardAdvisor citationGuardAdvisor(MessageService messageService) {
        return new CitationGuardAdvisor(messageService);
    }

    @Bean
    UserMemoryAdvisor userMemoryAdvisor(UserMemoryApi userMemoryApi, ProfilesApi profilesApi, StreaksApi streaksApi) {
        return new UserMemoryAdvisor(userMemoryApi, profilesApi, streaksApi);
    }

    @Bean
    ChatSummaryAdvisor chatSummaryAdvisor(ChatMemorySummaryRepository chatMemorySummaryRepository) {
        return new ChatSummaryAdvisor(chatMemorySummaryRepository);
    }

    @Bean
    ChatMemory coachChatMemory(ObjectProvider<ChatMemoryRepository> chatMemoryRepositoryProvider) {
        ChatMemoryRepository repository =
                chatMemoryRepositoryProvider.getIfAvailable(InMemoryChatMemoryRepository::new);
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(repository)
                .maxMessages(COACH_MEMORY_MAX_MESSAGES)
                .build();
    }

    @Bean
    MessageChatMemoryAdvisor coachChatMemoryAdvisor(ChatMemory coachChatMemory) {
        return MessageChatMemoryAdvisor.builder(coachChatMemory).build();
    }

    @Bean(name = COACH_CHAT_CLIENT)
    @ConditionalOnBean(IlmaiChatClientFactory.class)
    ChatClient coachChatClient(IlmaiChatClientFactory chatClientFactory,
                               CoachSystemPrompt coachSystemPrompt,
                               RetrieveTool retrieveTool,
                               GoalTool goalTool,
                               StartQuizTool startQuizTool,
                               GradeAnswerTool gradeAnswerTool,
                               GetQuizzesTool getQuizzesTool,
                               GetQuizTool getQuizTool,
                               GetGapsTool getGapsTool,
                               BuildPlanTool buildPlanTool,
                               GetPlanTool getPlanTool,
                               CompleteStepTool completeStepTool,
                               ImproviseTool improviseTool,
                               ReviewTool reviewTool,
                               UserMemoryAdvisor userMemoryAdvisor,
                               ChatSummaryAdvisor chatSummaryAdvisor,
                               GroundingGuardAdvisor groundingGuardAdvisor,
                               CitationGuardAdvisor citationGuardAdvisor,
                               MessageChatMemoryAdvisor coachChatMemoryAdvisor,
                               ObjectProvider<ToolCallingManager> toolCallingManagerProvider) {
        ChatClient.Builder builder = chatClientFactory.builder();
        if (builder == null) {
            log.warn("No chat model provider configured — Coach ChatClient will be unavailable");
            return null;
        }
        builder = builder
                .defaultSystem(coachSystemPrompt.get())
                .defaultTools(retrieveTool, goalTool, startQuizTool, gradeAnswerTool, getQuizzesTool,
                        getQuizTool, getGapsTool, buildPlanTool, getPlanTool, completeStepTool, improviseTool,
                        reviewTool)
                .defaultAdvisors(userMemoryAdvisor, chatSummaryAdvisor,
                        groundingGuardAdvisor, citationGuardAdvisor, coachChatMemoryAdvisor);
        ToolCallingManager toolCallingManager = toolCallingManagerProvider.getIfAvailable();
        if (toolCallingManager != null) {
            builder = builder.defaultAdvisors(ToolCallAdvisor.builder()
                    .toolCallingManager(toolCallingManager)
                    .conversationHistoryEnabled(false)
                    .build());
        }
        return builder.build();
    }

    @Bean(name = SUMMARY_CHAT_CLIENT)
    @ConditionalOnBean(IlmaiChatClientFactory.class)
    ChatClient summaryChatClient(IlmaiChatClientFactory chatClientFactory,
                                 SummarySystemPrompt summarySystemPrompt) {
        ChatClient.Builder builder = chatClientFactory.builder();
        if (builder == null) {
            log.warn("No chat model provider configured — summary ChatClient will be unavailable");
            return null;
        }
        return builder
                .defaultSystem(summarySystemPrompt.get())
                .build();
    }

    @Bean(name = FACTS_CHAT_CLIENT)
    @ConditionalOnBean(IlmaiChatClientFactory.class)
    ChatClient factsChatClient(IlmaiChatClientFactory chatClientFactory,
                               FactsSystemPrompt factsSystemPrompt) {
        ChatClient.Builder builder = chatClientFactory.builder();
        if (builder == null) {
            log.warn("No chat model provider configured — facts ChatClient will be unavailable");
            return null;
        }
        return builder
                .defaultSystem(factsSystemPrompt.get())
                .build();
    }

    @Bean(name = GAPS_NARRATOR_CHAT_CLIENT)
    @ConditionalOnBean(IlmaiChatClientFactory.class)
    ChatClient gapsNarratorChatClient(IlmaiChatClientFactory chatClientFactory,
                                      GapsNarratorSystemPrompt gapsNarratorSystemPrompt) {
        ChatClient.Builder builder = chatClientFactory.builder();
        if (builder == null) {
            log.warn("No chat model provider configured — gaps narrator ChatClient will be unavailable");
            return null;
        }
        return builder
                .defaultSystem(gapsNarratorSystemPrompt.get())
                .build();
    }

    @Bean(name = PLANNER_CHAT_CLIENT)
    @ConditionalOnBean(IlmaiChatClientFactory.class)
    ChatClient plannerChatClient(IlmaiChatClientFactory chatClientFactory,
                                 PlannerSystemPrompt plannerSystemPrompt) {
        ChatClient.Builder builder = chatClientFactory.builder();
        if (builder == null) {
            log.warn("No chat model provider configured — planner ChatClient will be unavailable");
            return null;
        }
        return builder
                .defaultSystem(plannerSystemPrompt.get())
                .build();
    }

    @Bean(name = DIGEST_NARRATOR_CHAT_CLIENT)
    @ConditionalOnBean(IlmaiChatClientFactory.class)
    ChatClient digestNarratorChatClient(IlmaiChatClientFactory chatClientFactory,
                                        DigestNarratorSystemPrompt digestNarratorSystemPrompt) {
        ChatClient.Builder builder = chatClientFactory.builder();
        if (builder == null) {
            log.warn("No chat model provider configured — digest narrator ChatClient will be unavailable");
            return null;
        }
        return builder
                .defaultSystem(digestNarratorSystemPrompt.get())
                .build();
    }
}
