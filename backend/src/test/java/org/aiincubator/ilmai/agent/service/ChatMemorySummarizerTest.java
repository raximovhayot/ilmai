package org.aiincubator.ilmai.agent.service;

import org.aiincubator.ilmai.agent.domain.ChatMemorySummary;
import org.aiincubator.ilmai.agent.domain.ChatMemorySummaryRepository;
import org.aiincubator.ilmai.common.CurrentUser;
import org.aiincubator.ilmai.common.quota.IlmTokenReservation;
import org.aiincubator.ilmai.common.quota.QuotaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class ChatMemorySummarizerTest {

    private final UUID userId = UUID.randomUUID();
    private final CurrentUser currentUser = new CurrentUser(userId);
    private final UUID sessionId = UUID.randomUUID();

    private ChatMemoryRepository chatMemoryRepository;
    private ChatMemorySummaryRepository summaryRepository;
    private ChatSummaryGenerator generator;
    private QuotaService quotaService;
    private PlatformTransactionManager transactionManager;
    private ChatMemorySummarizer summarizer;

    @BeforeEach
    void setUp() {
        chatMemoryRepository = mock(ChatMemoryRepository.class);
        summaryRepository = mock(ChatMemorySummaryRepository.class);
        generator = mock(ChatSummaryGenerator.class);
        quotaService = mock(QuotaService.class);
        transactionManager = mock(PlatformTransactionManager.class);
        ObjectProvider<ChatMemoryRepository> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(chatMemoryRepository);
        summarizer = new ChatMemorySummarizer(
                provider, summaryRepository, generator, quotaService, transactionManager);
    }

    @Test
    void foldsOldestTurnsWhenOverThreshold() {
        when(generator.isAvailable()).thenReturn(true);
        List<Message> messages = turns(ChatMemorySummarizer.DEFAULT_MAX_RAW_TURNS + 1);
        when(chatMemoryRepository.findByConversationId(sessionId.toString())).thenReturn(messages);
        when(quotaService.canSpend(userId, ChatMemorySummarizer.SUMMARY_ESTIMATE_ILM_TOKENS)).thenReturn(true);
        IlmTokenReservation reservation = mock(IlmTokenReservation.class);
        when(quotaService.reserve(userId, ChatMemorySummarizer.SUMMARY_ESTIMATE_ILM_TOKENS)).thenReturn(reservation);
        when(summaryRepository.findBySessionId(sessionId)).thenReturn(Optional.empty());
        when(generator.summarize(isNull(), anyList())).thenReturn(new ChatMemorySummaryDraft("folded summary", 3));

        summarizer.maintain(currentUser, sessionId);

        int foldedMessageCount = ChatMemorySummarizer.DEFAULT_FOLD_TURNS * 2;
        ArgumentCaptor<List<Message>> foldCaptor = ArgumentCaptor.forClass(List.class);
        verify(generator).summarize(isNull(), foldCaptor.capture());
        assertThat(foldCaptor.getValue()).hasSize(foldedMessageCount);

        verify(quotaService).commit(reservation, 3);
        verify(quotaService, never()).refund(any());

        ArgumentCaptor<ChatMemorySummary> saveCaptor = ArgumentCaptor.forClass(ChatMemorySummary.class);
        verify(summaryRepository).save(saveCaptor.capture());
        assertThat(saveCaptor.getValue().getSessionId()).isEqualTo(sessionId);
        assertThat(saveCaptor.getValue().getSummary()).isEqualTo("folded summary");
        assertThat(saveCaptor.getValue().getFoldedMessages()).isEqualTo(foldedMessageCount);

        ArgumentCaptor<List<Message>> retainCaptor = ArgumentCaptor.forClass(List.class);
        verify(chatMemoryRepository).saveAll(eq(sessionId.toString()), retainCaptor.capture());
        assertThat(retainCaptor.getValue()).hasSize(messages.size() - foldedMessageCount);
    }

    @Test
    void noOpWhenAtOrBelowThreshold() {
        when(generator.isAvailable()).thenReturn(true);
        when(chatMemoryRepository.findByConversationId(sessionId.toString()))
                .thenReturn(turns(ChatMemorySummarizer.DEFAULT_MAX_RAW_TURNS));

        summarizer.maintain(currentUser, sessionId);

        verify(generator, never()).summarize(any(), anyList());
        verify(quotaService, never()).reserve(any(), anyInt());
        verify(chatMemoryRepository, never()).saveAll(any(), anyList());
    }

    @Test
    void skipsFoldWhenQuotaExceeded() {
        when(generator.isAvailable()).thenReturn(true);
        when(chatMemoryRepository.findByConversationId(sessionId.toString()))
                .thenReturn(turns(ChatMemorySummarizer.DEFAULT_MAX_RAW_TURNS + 1));
        when(quotaService.canSpend(userId, ChatMemorySummarizer.SUMMARY_ESTIMATE_ILM_TOKENS)).thenReturn(false);

        summarizer.maintain(currentUser, sessionId);

        verify(quotaService, never()).reserve(any(), anyInt());
        verify(generator, never()).summarize(any(), anyList());
        verify(chatMemoryRepository, never()).saveAll(any(), anyList());
    }

    @Test
    void refundsWhenGeneratorReturnsNull() {
        when(generator.isAvailable()).thenReturn(true);
        when(chatMemoryRepository.findByConversationId(sessionId.toString()))
                .thenReturn(turns(ChatMemorySummarizer.DEFAULT_MAX_RAW_TURNS + 1));
        when(quotaService.canSpend(userId, ChatMemorySummarizer.SUMMARY_ESTIMATE_ILM_TOKENS)).thenReturn(true);
        IlmTokenReservation reservation = mock(IlmTokenReservation.class);
        when(quotaService.reserve(userId, ChatMemorySummarizer.SUMMARY_ESTIMATE_ILM_TOKENS)).thenReturn(reservation);
        when(summaryRepository.findBySessionId(sessionId)).thenReturn(Optional.empty());
        when(generator.summarize(isNull(), anyList())).thenReturn(null);

        summarizer.maintain(currentUser, sessionId);

        verify(quotaService).refund(reservation);
        verify(quotaService, never()).commit(any(), anyInt());
        verify(summaryRepository, never()).save(any());
        verify(chatMemoryRepository, never()).saveAll(any(), anyList());
    }

    @Test
    void noOpWhenNoChatMemoryRepository() {
        ObjectProvider<ChatMemoryRepository> emptyProvider = mock(ObjectProvider.class);
        when(emptyProvider.getIfAvailable()).thenReturn(null);
        ChatMemorySummarizer noRepoSummarizer = new ChatMemorySummarizer(
                emptyProvider, summaryRepository, generator, quotaService, transactionManager);

        noRepoSummarizer.maintain(currentUser, sessionId);

        verifyNoInteractions(generator, quotaService, summaryRepository);
    }

    private List<Message> turns(int count) {
        List<Message> messages = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            messages.add(new UserMessage("question " + i));
            messages.add(new AssistantMessage("answer " + i));
        }
        return messages;
    }
}
