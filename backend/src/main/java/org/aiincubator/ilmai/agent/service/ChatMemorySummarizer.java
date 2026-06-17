package org.aiincubator.ilmai.agent.service;

import lombok.extern.slf4j.Slf4j;
import org.aiincubator.ilmai.agent.domain.ChatMemorySummary;
import org.aiincubator.ilmai.agent.domain.ChatMemorySummaryRepository;
import org.aiincubator.ilmai.common.CurrentUser;
import org.aiincubator.ilmai.common.quota.IlmTokenReservation;
import org.aiincubator.ilmai.common.quota.QuotaService;
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
public class ChatMemorySummarizer {

    static final int DEFAULT_MAX_RAW_TURNS = 8;
    static final int DEFAULT_FOLD_TURNS = 4;
    static final int SUMMARY_ESTIMATE_ILM_TOKENS = 2;

    private final ChatMemoryRepository chatMemoryRepository;
    private final ChatMemorySummaryRepository summaryRepository;
    private final ChatSummaryGenerator generator;
    private final QuotaService quotaService;
    private final TransactionTemplate transactionTemplate;
    private final int maxRawTurns;
    private final int foldTurns;

    public ChatMemorySummarizer(ObjectProvider<ChatMemoryRepository> chatMemoryRepositoryProvider,
                                ChatMemorySummaryRepository summaryRepository,
                                ChatSummaryGenerator generator,
                                QuotaService quotaService,
                                PlatformTransactionManager transactionManager) {
        this.chatMemoryRepository = chatMemoryRepositoryProvider.getIfAvailable();
        this.summaryRepository = summaryRepository;
        this.generator = generator;
        this.quotaService = quotaService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.maxRawTurns = DEFAULT_MAX_RAW_TURNS;
        this.foldTurns = DEFAULT_FOLD_TURNS;
    }

    public void maintain(CurrentUser currentUser, UUID sessionId) {
        if (currentUser == null || sessionId == null) {
            return;
        }
        if (chatMemoryRepository == null || !generator.isAvailable()) {
            return;
        }
        List<List<Message>> turns = splitIntoTurns(chatMemoryRepository.findByConversationId(sessionId.toString()));
        if (turns.size() <= maxRawTurns) {
            return;
        }
        int foldCount = Math.min(foldTurns, turns.size() - 1);
        if (foldCount <= 0) {
            return;
        }
        List<Message> foldedMessages = flatten(turns.subList(0, foldCount));
        List<Message> retainedMessages = flatten(turns.subList(foldCount, turns.size()));
        if (foldedMessages.isEmpty()) {
            return;
        }

        if (!quotaService.canSpend(currentUser.getUserId(), SUMMARY_ESTIMATE_ILM_TOKENS)) {
            log.debug("chat-summary: skipping fold, quota-exceeded user={} session={}",
                    currentUser.getUserId(), sessionId);
            return;
        }
        IlmTokenReservation reservation = quotaService.reserve(
                currentUser.getUserId(), SUMMARY_ESTIMATE_ILM_TOKENS);
        boolean committed = false;
        try {
            String existingSummary = summaryRepository.findBySessionId(sessionId)
                    .map(ChatMemorySummary::getSummary)
                    .orElse(null);
            ChatMemorySummaryDraft draft = generator.summarize(existingSummary, foldedMessages);
            if (draft == null) {
                return;
            }
            quotaService.commit(reservation, draft.getIlmTokenCost());
            committed = true;
            persistFold(sessionId, draft.getSummary(), foldedMessages.size(), retainedMessages);
            log.debug("chat-summary: folded {} messages user={} session={} actualIlmTokens={}",
                    foldedMessages.size(), currentUser.getUserId(), sessionId, draft.getIlmTokenCost());
        } finally {
            if (!committed) {
                quotaService.refund(reservation);
            }
        }
    }

    private void persistFold(UUID sessionId, String summary, int foldedCount, List<Message> retained) {
        transactionTemplate.executeWithoutResult(status -> {
            ChatMemorySummary entity = summaryRepository.findBySessionId(sessionId)
                    .orElseGet(() -> {
                        ChatMemorySummary created = new ChatMemorySummary();
                        created.setSessionId(sessionId);
                        return created;
                    });
            entity.setSummary(summary);
            entity.setFoldedMessages(entity.getFoldedMessages() + foldedCount);
            summaryRepository.save(entity);
            chatMemoryRepository.saveAll(sessionId.toString(), retained);
        });
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
