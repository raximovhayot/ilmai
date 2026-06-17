package org.aiincubator.ilmai.agent.service;

import org.aiincubator.ilmai.agent.ChatChannel;
import org.aiincubator.ilmai.agent.MessagePart;
import org.aiincubator.ilmai.agent.QuizCardPart;
import org.aiincubator.ilmai.agent.TextPart;
import org.aiincubator.ilmai.ai.IlmaiChatClientFactory;
import org.aiincubator.ilmai.common.CurrentUser;
import org.aiincubator.ilmai.common.i18n.MessageService;
import org.aiincubator.ilmai.common.quota.IlmTokenReservation;
import org.aiincubator.ilmai.common.quota.QuotaService;
import org.aiincubator.ilmai.quiz.QuizCardDto;
import org.aiincubator.ilmai.quiz.QuizCardQuestionDto;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class DefaultAgentApiQuizCardEmissionTest {

    @Test
    void emitsQuizCardPartWhenTurnRecordsAQuiz() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID quizSessionId = UUID.randomUUID();
        UUID questionId = UUID.randomUUID();
        UUID materialId = UUID.randomUUID();
        CurrentUser currentUser = new CurrentUser(userId);

        QuotaService quotaService = mock(QuotaService.class);
        when(quotaService.canSpend(userId, CoachTurnSupport.PER_TURN_ESTIMATE_ILM_TOKENS)).thenReturn(true);
        when(quotaService.reserve(userId, CoachTurnSupport.PER_TURN_ESTIMATE_ILM_TOKENS))
                .thenReturn(mock(IlmTokenReservation.class));

        ChatClient client = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        ChatResponse response = ChatResponse.builder()
                .generations(List.of(new Generation(new AssistantMessage("here is your quiz"))))
                .build();
        when(client.prompt().user(anyString()).advisors(any(Consumer.class)).tools(any(Consumer.class)).call().chatResponse())
                .thenAnswer(invocation -> {
                    AgentQuizContext current = AgentQuizContext.current();
                    if (current != null) {
                        current.record(new QuizCardDto(quizSessionId, null, "SOLID", "EN", List.of(
                                new QuizCardQuestionDto(questionId, 1, "MULTIPLE_CHOICE", "concept", "prompt?",
                                        List.of("a", "b", "c", "d"), materialId, "Notes", 2))));
                    }
                    return response;
                });

        ObjectProvider<ChatClient> chatClientProvider = mock(ObjectProvider.class);
        when(chatClientProvider.getIfAvailable()).thenReturn(client);
        ObjectProvider<IlmaiChatClientFactory> factoryProvider = mock(ObjectProvider.class);
        when(factoryProvider.getIfAvailable()).thenReturn(null);

        CoachTurnSupport turnSupport = new CoachTurnSupport(
                quotaService,
                mock(IlmTokenCostCalculator.class),
                factoryProvider,
                mock(ChatMemorySummarizer.class),
                mock(UserFactExtractor.class),
                mock(ApplicationEventPublisher.class));
        DefaultAgentApi agentApi = new DefaultAgentApi(
                chatClientProvider,
                mock(MessageService.class),
                mock(ChatSessionService.class),
                turnSupport);

        List<MessagePart> parts = agentApi
                .chat(currentUser, sessionId, "quiz me on photosynthesis", ChatChannel.WEB)
                .collectList()
                .block();

        assertThat(parts).isNotNull();
        assertThat(parts).anySatisfy(part -> assertThat(part).isInstanceOf(TextPart.class));
        assertThat(parts).anySatisfy(part -> {
            assertThat(part).isInstanceOf(QuizCardPart.class);
            QuizCardPart card = (QuizCardPart) part;
            assertThat(card.getSessionId()).isEqualTo(quizSessionId);
            assertThat(card.getQuestionId()).isEqualTo(questionId);
            assertThat(card.getMaterialId()).isEqualTo(materialId);
            assertThat(card.getPrompt()).isEqualTo("prompt?");
        });
    }
}
