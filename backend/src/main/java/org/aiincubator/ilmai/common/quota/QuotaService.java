package org.aiincubator.ilmai.common.quota;

import java.util.UUID;

public interface QuotaService {

    int dailyQuizQuota(UUID userId);

    int dailyChatMessageQuota(UUID userId);

    int materialUploadQuota(UUID userId);

    long materialUploadMaxBytes(UUID userId);

    boolean isPremiumFeatureAllowed(UUID userId, PremiumFeature feature);

    int dailyIlmTokenAllowance(UUID userId);

    int remainingIlmTokensToday(UUID userId);

    boolean canSpend(UUID userId, int ilmTokens);

    IlmTokenReservation reserve(UUID userId, int estimatedIlmTokens);

    void commit(IlmTokenReservation reservation, int actualIlmTokensSpent);

    void refund(IlmTokenReservation reservation);
}
