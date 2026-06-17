package org.aiincubator.ilmai.agent.service;

import lombok.extern.slf4j.Slf4j;
import org.aiincubator.ilmai.ai.IlmaiChatClientFactory;
import org.aiincubator.ilmai.common.CurrentUser;
import org.aiincubator.ilmai.common.UserActivityRecordedEvent;
import org.aiincubator.ilmai.common.quota.IlmTokenReservation;
import org.aiincubator.ilmai.common.quota.QuotaService;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@Slf4j
public class CoachTurnSupport {

    static final int PER_TURN_ESTIMATE_ILM_TOKENS = 5;
    static final String QUOTA_EXCEEDED_MESSAGE_KEY = "agent.quota.exceeded";

    private final QuotaService quotaService;
    private final IlmTokenCostCalculator costCalculator;
    private final IlmaiChatClientFactory chatClientFactory;
    private final ChatMemorySummarizer chatMemorySummarizer;
    private final UserFactExtractor userFactExtractor;
    private final ApplicationEventPublisher eventPublisher;

    public CoachTurnSupport(
            QuotaService quotaService,
            IlmTokenCostCalculator costCalculator,
            ObjectProvider<IlmaiChatClientFactory> chatClientFactoryProvider,
            ChatMemorySummarizer chatMemorySummarizer,
            UserFactExtractor userFactExtractor,
            ApplicationEventPublisher eventPublisher) {
        this.quotaService = quotaService;
        this.costCalculator = costCalculator;
        this.chatClientFactory = chatClientFactoryProvider.getIfAvailable();
        this.chatMemorySummarizer = chatMemorySummarizer;
        this.userFactExtractor = userFactExtractor;
        this.eventPublisher = eventPublisher;
    }

    public boolean canSpend(CurrentUser currentUser) {
        return quotaService.canSpend(currentUser.getUserId(), PER_TURN_ESTIMATE_ILM_TOKENS);
    }

    public IlmTokenReservation reserve(CurrentUser currentUser) {
        return quotaService.reserve(currentUser.getUserId(), PER_TURN_ESTIMATE_ILM_TOKENS);
    }

    public int commit(IlmTokenReservation reservation, ChatResponse response) {
        int actualIlmTokens = computeActualCost(response);
        quotaService.commit(reservation, actualIlmTokens);
        return actualIlmTokens;
    }

    public void refund(IlmTokenReservation reservation) {
        quotaService.refund(reservation);
    }

    public void completeTurnQuietly(CurrentUser currentUser, UUID sessionId) {
        maintainSummaryQuietly(currentUser, sessionId);
        extractFactsQuietly(currentUser, sessionId);
        publishActivityRecorded(currentUser);
    }

    private void maintainSummaryQuietly(CurrentUser currentUser, UUID sessionId) {
        try {
            chatMemorySummarizer.maintain(currentUser, sessionId);
        } catch (RuntimeException ex) {
            log.warn("agent.chat summary maintenance failed user={} session={}: {}",
                    currentUser.getUserId(), sessionId, ex.toString());
        }
    }

    private void extractFactsQuietly(CurrentUser currentUser, UUID sessionId) {
        try {
            userFactExtractor.extract(currentUser, sessionId);
        } catch (RuntimeException ex) {
            log.warn("agent.chat fact extraction failed user={} session={}: {}",
                    currentUser.getUserId(), sessionId, ex.toString());
        }
    }

    private void publishActivityRecorded(CurrentUser currentUser) {
        try {
            eventPublisher.publishEvent(
                    new UserActivityRecordedEvent(currentUser.getUserId(), OffsetDateTime.now()));
        } catch (RuntimeException ex) {
            log.warn("agent.chat activity event publish failed user={}: {}",
                    currentUser.getUserId(), ex.toString());
        }
    }

    private int computeActualCost(ChatResponse response) {
        if (response == null) {
            return 0;
        }
        ChatResponseMetadata metadata = response.getMetadata();
        if (metadata == null) {
            return 0;
        }
        Usage usage = metadata.getUsage();
        long prompt = usage == null || usage.getPromptTokens() == null ? 0L : usage.getPromptTokens();
        long completion = usage == null || usage.getCompletionTokens() == null ? 0L : usage.getCompletionTokens();
        String provider = chatClientFactory != null ? chatClientFactory.defaultProvider() : null;
        String model = metadata.getModel();
        return costCalculator.costInIlmTokens(provider, model, prompt, completion);
    }
}
