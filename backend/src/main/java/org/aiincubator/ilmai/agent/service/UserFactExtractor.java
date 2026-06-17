package org.aiincubator.ilmai.agent.service;

import lombok.extern.slf4j.Slf4j;
import org.aiincubator.ilmai.agent.domain.UserFactExtractionState;
import org.aiincubator.ilmai.agent.domain.UserFactExtractionStateRepository;
import org.aiincubator.ilmai.common.CurrentUser;
import org.aiincubator.ilmai.common.quota.IlmTokenReservation;
import org.aiincubator.ilmai.common.quota.QuotaService;
import org.aiincubator.ilmai.agent.UserMemoryApi;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class UserFactExtractor {

    static final int DEFAULT_EXTRACT_EVERY_TURNS = 4;
    static final int DEFAULT_RECENT_TURNS = 4;
    static final int FACTS_ESTIMATE_ILM_TOKENS = 2;

    private final ChatMemoryRepository chatMemoryRepository;
    private final UserFactExtractionStateRepository stateRepository;
    private final UserFactGenerator generator;
    private final UserMemoryApi userMemoryApi;
    private final QuotaService quotaService;
    private final TransactionTemplate transactionTemplate;
    private final int extractEveryTurns;
    private final int recentTurns;

    public UserFactExtractor(ObjectProvider<ChatMemoryRepository> chatMemoryRepositoryProvider,
                             UserFactExtractionStateRepository stateRepository,
                             UserFactGenerator generator,
                             UserMemoryApi userMemoryApi,
                             QuotaService quotaService,
                             PlatformTransactionManager transactionManager) {
        this.chatMemoryRepository = chatMemoryRepositoryProvider.getIfAvailable();
        this.stateRepository = stateRepository;
        this.generator = generator;
        this.userMemoryApi = userMemoryApi;
        this.quotaService = quotaService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.extractEveryTurns = DEFAULT_EXTRACT_EVERY_TURNS;
        this.recentTurns = DEFAULT_RECENT_TURNS;
    }

    public void extract(CurrentUser currentUser, UUID sessionId) {
        if (currentUser == null || sessionId == null) {
            return;
        }
        if (chatMemoryRepository == null || !generator.isAvailable()) {
            return;
        }
        int turns = recordTurn(sessionId);
        if (turns % extractEveryTurns != 0) {
            return;
        }
        List<Message> recent = lastTurns(
                chatMemoryRepository.findByConversationId(sessionId.toString()), recentTurns);
        if (recent.isEmpty()) {
            return;
        }
        if (!quotaService.canSpend(currentUser.getUserId(), FACTS_ESTIMATE_ILM_TOKENS)) {
            log.debug("user-facts: skipping extraction, quota-exceeded user={} session={}",
                    currentUser.getUserId(), sessionId);
            return;
        }
        IlmTokenReservation reservation = quotaService.reserve(
                currentUser.getUserId(), FACTS_ESTIMATE_ILM_TOKENS);
        boolean committed = false;
        try {
            UserFactExtractionDraft draft = generator.extract(recent);
            if (draft == null) {
                return;
            }
            quotaService.commit(reservation, draft.getIlmTokenCost());
            committed = true;
            int written = userMemoryApi.recordFacts(currentUser, draft.getFacts());
            log.debug("user-facts: extracted {} fact(s) user={} session={} actualIlmTokens={}",
                    written, currentUser.getUserId(), sessionId, draft.getIlmTokenCost());
        } finally {
            if (!committed) {
                quotaService.refund(reservation);
            }
        }
    }

    private int recordTurn(UUID sessionId) {
        return transactionTemplate.execute(status -> {
            UserFactExtractionState state = stateRepository.findBySessionId(sessionId)
                    .orElseGet(() -> {
                        UserFactExtractionState created = new UserFactExtractionState();
                        created.setSessionId(sessionId);
                        return created;
                    });
            state.setTurnsSeen(state.getTurnsSeen() + 1);
            return stateRepository.save(state).getTurnsSeen();
        });
    }

    private List<Message> lastTurns(List<Message> messages, int maxTurns) {
        List<List<Message>> turns = splitIntoTurns(messages);
        int from = Math.max(0, turns.size() - maxTurns);
        return flatten(turns.subList(from, turns.size()));
    }

    private List<List<Message>> splitIntoTurns(List<Message> messages) {
        List<List<Message>> turns = new ArrayList<>();
        List<Message> current = null;
        for (Message message : messages) {
            if (message.getMessageType() == MessageType.USER || current == null) {
                current = new ArrayList<>();
                turns.add(current);
            }
            current.add(message);
        }
        return turns;
    }

    private List<Message> flatten(List<List<Message>> turns) {
        List<Message> flattened = new ArrayList<>();
        for (List<Message> turn : turns) {
            flattened.addAll(turn);
        }
        return flattened;
    }
}
