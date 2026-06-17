package org.aiincubator.ilmai.agent.service;

import org.aiincubator.ilmai.agent.ChatChannel;
import org.aiincubator.ilmai.agent.MessagePart;
import org.aiincubator.ilmai.agent.TextPart;
import org.aiincubator.ilmai.common.UserActivityRecordedEvent;
import org.aiincubator.ilmai.ai.IlmaiChatClientFactory;
import org.aiincubator.ilmai.common.CurrentUser;
import org.aiincubator.ilmai.common.i18n.MessageService;
import org.aiincubator.ilmai.common.quota.IlmTokenReservation;
import org.aiincubator.ilmai.common.quota.QuotaService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class DefaultAgentApiActivityEventTest {

    @Test
    void successfulTurnPublishesActivityEventAndTriggersFactExtraction() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        CurrentUser currentUser = new CurrentUser(userId);

        QuotaService quotaService = mock(QuotaService.class);
        when(quotaService.canSpend(userId, CoachTurnSupport.PER_TURN_ESTIMATE_ILM_TOKENS)).thenReturn(true);
        when(quotaService.reserve(userId, CoachTurnSupport.PER_TURN_ESTIMATE_ILM_TOKENS))
                .thenReturn(mock(IlmTokenReservation.class));

        ChatClient client = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        ChatResponse response = ChatResponse.builder()
                .generations(List.of(new Generation(new AssistantMessage("the answer"))))
                .build();
        when(client.prompt().user(anyString()).advisors(any(Consumer.class)).tools(any(Consumer.class)).call().chatResponse())
                .thenReturn(response);

        ObjectProvider<ChatClient> chatClientProvider = mock(ObjectProvider.class);
        when(chatClientProvider.getIfAvailable()).thenReturn(client);
        ObjectProvider<IlmaiChatClientFactory> factoryProvider = mock(ObjectProvider.class);
        when(factoryProvider.getIfAvailable()).thenReturn(null);

        ChatSessionService chatSessionService = mock(ChatSessionService.class);
        UserFactExtractor userFactExtractor = mock(UserFactExtractor.class);
        ChatMemorySummarizer chatMemorySummarizer = mock(ChatMemorySummarizer.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);

        CoachTurnSupport turnSupport = new CoachTurnSupport(
                quotaService,
                mock(IlmTokenCostCalculator.class),
                factoryProvider,
                chatMemorySummarizer,
                userFactExtractor,
                eventPublisher);
        DefaultAgentApi agentApi = new DefaultAgentApi(
                chatClientProvider,
                mock(MessageService.class),
                chatSessionService,
                turnSupport);

        List<MessagePart> parts = agentApi
                .chat(currentUser, sessionId, "hello", ChatChannel.WEB)
                .collectList()
                .block();

        assertThat(parts).isNotNull();
        assertThat(parts).anySatisfy(part -> {
            assertThat(part).isInstanceOf(TextPart.class);
            assertThat(((TextPart) part).getText()).isEqualTo("the answer");
        });

        verify(chatSessionService).requireOwnedSession(currentUser, sessionId);
        verify(userFactExtractor).extract(currentUser, sessionId);
        verify(chatMemorySummarizer).maintain(currentUser, sessionId);

        ArgumentCaptor<UserActivityRecordedEvent> eventCaptor =
                ArgumentCaptor.forClass(UserActivityRecordedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getUserId()).isEqualTo(userId);
        assertThat(eventCaptor.getValue().getOccurredAt()).isNotNull();
    }
}
