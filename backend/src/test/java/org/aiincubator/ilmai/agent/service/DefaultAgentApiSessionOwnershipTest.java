package org.aiincubator.ilmai.agent.service;

import org.aiincubator.ilmai.agent.ChatChannel;
import org.aiincubator.ilmai.ai.IlmaiChatClientFactory;
import org.aiincubator.ilmai.common.CurrentUser;
import org.aiincubator.ilmai.common.i18n.MessageService;
import org.aiincubator.ilmai.common.quota.QuotaService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultAgentApiSessionOwnershipTest {

    @SuppressWarnings("unchecked")
    @Test
    void chatRejectsUnownedSessionBeforeQuotaOrLlm() {
        QuotaService quotaService = mock(QuotaService.class);
        ObjectProvider<ChatClient> chatClientProvider = mock(ObjectProvider.class);
        ObjectProvider<IlmaiChatClientFactory> factoryProvider = mock(ObjectProvider.class);
        when(factoryProvider.getIfAvailable()).thenReturn(null);
        ChatSessionService chatSessionService = mock(ChatSessionService.class);

        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        CurrentUser currentUser = new CurrentUser(userId);
        when(chatSessionService.requireOwnedSession(currentUser, sessionId))
                .thenThrow(new ChatSessionException(ChatSessionException.Reason.SESSION_NOT_FOUND));

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
                chatSessionService,
                turnSupport,
                mock(ChatTranscriptService.class));

        assertThatThrownBy(() -> agentApi.chat(currentUser, sessionId, "hello", ChatChannel.WEB))
                .isInstanceOf(ChatSessionException.class);

        verify(quotaService, never()).canSpend(any(UUID.class), anyInt());
        verify(chatClientProvider, never()).getIfAvailable();
    }
}
