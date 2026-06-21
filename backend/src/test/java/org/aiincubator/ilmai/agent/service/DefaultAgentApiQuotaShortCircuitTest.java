package org.aiincubator.ilmai.agent.service;

import org.aiincubator.ilmai.agent.AgentErrorCodes;
import org.aiincubator.ilmai.agent.ChatChannel;
import org.aiincubator.ilmai.agent.ErrorPart;
import org.aiincubator.ilmai.agent.MessagePart;
import org.aiincubator.ilmai.ai.IlmaiChatClientFactory;
import org.aiincubator.ilmai.common.CurrentUser;
import org.aiincubator.ilmai.common.i18n.MessageService;
import org.aiincubator.ilmai.common.quota.QuotaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultAgentApiQuotaShortCircuitTest {

    private QuotaService quotaService;
    private ObjectProvider<ChatClient> chatClientProvider;
    private DefaultAgentApi agentApi;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        quotaService = mock(QuotaService.class);
        chatClientProvider = mock(ObjectProvider.class);
        ObjectProvider<IlmaiChatClientFactory> factoryProvider = mock(ObjectProvider.class);
        MessageService messageService = mock(MessageService.class);
        when(messageService.get(CoachTurnSupport.QUOTA_EXCEEDED_MESSAGE_KEY))
                .thenReturn("you've used today's allowance");
        when(factoryProvider.getIfAvailable()).thenReturn(null);

        CoachTurnSupport turnSupport = new CoachTurnSupport(
                quotaService,
                mock(IlmTokenCostCalculator.class),
                factoryProvider,
                mock(ChatMemorySummarizer.class),
                mock(UserFactExtractor.class),
                mock(ApplicationEventPublisher.class));
        agentApi = new DefaultAgentApi(
                chatClientProvider,
                messageService,
                mock(ChatSessionService.class),
                turnSupport,
                mock(ChatTranscriptService.class));
    }

    @Test
    void zeroAllowanceUserIsShortCircuitedWithQuotaExceeded() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        CurrentUser currentUser = new CurrentUser(userId);
        when(quotaService.canSpend(userId, CoachTurnSupport.PER_TURN_ESTIMATE_ILM_TOKENS))
                .thenReturn(false);

        List<MessagePart> parts = agentApi
                .chat(currentUser, sessionId, "hello", ChatChannel.WEB)
                .collectList()
                .block();

        assertThat(parts).hasSize(1);
        MessagePart only = parts.getFirst();
        assertThat(only).isInstanceOf(ErrorPart.class);
        ErrorPart error = (ErrorPart) only;
        assertThat(error.getCode()).isEqualTo(AgentErrorCodes.QUOTA_EXCEEDED);
        assertThat(error.getMessage()).isEqualTo("you've used today's allowance");
        assertThat(error.isRetryable()).isFalse();

        verify(chatClientProvider, never()).getIfAvailable();
        verify(quotaService, never()).reserve(any(UUID.class), anyInt());
    }
}
