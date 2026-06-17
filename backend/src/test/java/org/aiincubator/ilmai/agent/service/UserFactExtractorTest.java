package org.aiincubator.ilmai.agent.service;

import org.aiincubator.ilmai.agent.domain.UserFactExtractionState;
import org.aiincubator.ilmai.agent.domain.UserFactExtractionStateRepository;
import org.aiincubator.ilmai.common.CurrentUser;
import org.aiincubator.ilmai.common.quota.IlmTokenReservation;
import org.aiincubator.ilmai.common.quota.QuotaService;
import org.aiincubator.ilmai.agent.UserMemoryApi;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class UserFactExtractorTest {

    private final UUID userId = UUID.randomUUID();
    private final CurrentUser currentUser = new CurrentUser(userId);
    private final UUID sessionId = UUID.randomUUID();

    private ChatMemoryRepository chatMemoryRepository;
    private UserFactExtractionStateRepository stateRepository;
    private UserFactGenerator generator;
    private UserMemoryApi userMemoryApi;
    private QuotaService quotaService;
    private PlatformTransactionManager transactionManager;
    private UserFactExtractor extractor;

    @BeforeEach
    void setUp() {
        chatMemoryRepository = mock(ChatMemoryRepository.class);
        stateRepository = mock(UserFactExtractionStateRepository.class);
        generator = mock(UserFactGenerator.class);
        userMemoryApi = mock(UserMemoryApi.class);
        quotaService = mock(QuotaService.class);
        transactionManager = mock(PlatformTransactionManager.class);
        when(stateRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        ObjectProvider<ChatMemoryRepository> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(chatMemoryRepository);
        extractor = new UserFactExtractor(
                provider, stateRepository, generator, userMemoryApi, quotaService, transactionManager);
    }

    @Test
    void noOpBeforeThreshold() {
        when(generator.isAvailable()).thenReturn(true);
        when(stateRepository.findBySessionId(sessionId)).thenReturn(Optional.of(state(0)));

        extractor.extract(currentUser, sessionId);

        ArgumentCaptor<UserFactExtractionState> saveCaptor =
                ArgumentCaptor.forClass(UserFactExtractionState.class);
        verify(stateRepository).save(saveCaptor.capture());
        assertThat(saveCaptor.getValue().getTurnsSeen()).isEqualTo(1);

        verify(chatMemoryRepository, never()).findByConversationId(any());
        verify(generator, never()).extract(anyList());
        verify(quotaService, never()).canSpend(any(), anyInt());
        verify(userMemoryApi, never()).recordFacts(any(), anyList());
    }

    @Test
    void extractsAndRecordsFactsAtThreshold() {
        when(generator.isAvailable()).thenReturn(true);
        when(stateRepository.findBySessionId(sessionId))
                .thenReturn(Optional.of(state(UserFactExtractor.DEFAULT_EXTRACT_EVERY_TURNS - 1)));
        when(chatMemoryRepository.findByConversationId(sessionId.toString())).thenReturn(turns(2));
        when(quotaService.canSpend(userId, UserFactExtractor.FACTS_ESTIMATE_ILM_TOKENS)).thenReturn(true);
        IlmTokenReservation reservation = mock(IlmTokenReservation.class);
        when(quotaService.reserve(userId, UserFactExtractor.FACTS_ESTIMATE_ILM_TOKENS)).thenReturn(reservation);
        List<String> facts = List.of("the learner prefers worked examples", "the learner studies for IELTS");
        when(generator.extract(anyList())).thenReturn(new UserFactExtractionDraft(facts, 3));
        when(userMemoryApi.recordFacts(eq(currentUser), anyList())).thenReturn(facts.size());

        extractor.extract(currentUser, sessionId);

        verify(quotaService).commit(reservation, 3);
        verify(quotaService, never()).refund(any());
        ArgumentCaptor<List<String>> factsCaptor = ArgumentCaptor.forClass(List.class);
        verify(userMemoryApi).recordFacts(eq(currentUser), factsCaptor.capture());
        assertThat(factsCaptor.getValue()).containsExactlyElementsOf(facts);
    }

    @Test
    void skipsExtractionWhenQuotaExceeded() {
        when(generator.isAvailable()).thenReturn(true);
        when(stateRepository.findBySessionId(sessionId))
                .thenReturn(Optional.of(state(UserFactExtractor.DEFAULT_EXTRACT_EVERY_TURNS - 1)));
        when(chatMemoryRepository.findByConversationId(sessionId.toString())).thenReturn(turns(2));
        when(quotaService.canSpend(userId, UserFactExtractor.FACTS_ESTIMATE_ILM_TOKENS)).thenReturn(false);

        extractor.extract(currentUser, sessionId);

        verify(quotaService, never()).reserve(any(), anyInt());
        verify(generator, never()).extract(anyList());
        verify(userMemoryApi, never()).recordFacts(any(), anyList());
    }

    @Test
    void refundsWhenGeneratorReturnsNull() {
        when(generator.isAvailable()).thenReturn(true);
        when(stateRepository.findBySessionId(sessionId))
                .thenReturn(Optional.of(state(UserFactExtractor.DEFAULT_EXTRACT_EVERY_TURNS - 1)));
        when(chatMemoryRepository.findByConversationId(sessionId.toString())).thenReturn(turns(2));
        when(quotaService.canSpend(userId, UserFactExtractor.FACTS_ESTIMATE_ILM_TOKENS)).thenReturn(true);
        IlmTokenReservation reservation = mock(IlmTokenReservation.class);
        when(quotaService.reserve(userId, UserFactExtractor.FACTS_ESTIMATE_ILM_TOKENS)).thenReturn(reservation);
        when(generator.extract(anyList())).thenReturn(null);

        extractor.extract(currentUser, sessionId);

        verify(quotaService).refund(reservation);
        verify(quotaService, never()).commit(any(), anyInt());
        verify(userMemoryApi, never()).recordFacts(any(), anyList());
    }

    @Test
    void noOpWhenNoChatMemoryRepository() {
        ObjectProvider<ChatMemoryRepository> emptyProvider = mock(ObjectProvider.class);
        when(emptyProvider.getIfAvailable()).thenReturn(null);
        UserFactExtractor noRepoExtractor = new UserFactExtractor(
                emptyProvider, stateRepository, generator, userMemoryApi, quotaService, transactionManager);

        noRepoExtractor.extract(currentUser, sessionId);

        verifyNoInteractions(generator, quotaService, userMemoryApi);
        verify(stateRepository, never()).save(any());
    }

    private UserFactExtractionState state(int turnsSeen) {
        UserFactExtractionState state = new UserFactExtractionState();
        state.setSessionId(sessionId);
        state.setTurnsSeen(turnsSeen);
        return state;
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
