package org.aiincubator.ilmai.billing.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aiincubator.ilmai.billing.config.BillingProperties;
import org.aiincubator.ilmai.common.quota.IlmTokenQuotaProperties;
import org.aiincubator.ilmai.common.quota.IlmTokenReservation;
import org.aiincubator.ilmai.common.quota.PremiumFeature;
import org.aiincubator.ilmai.common.quota.QuotaService;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class BillingQuotaService implements QuotaService {

    private final BillingProperties properties;
    private final IlmTokenQuotaProperties quotaProperties;
    private final BillingService billingService;
    private final Clock clock;

    private final ConcurrentMap<UUID, UserDailyIlmTokenBucket> buckets = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, IlmTokenReservation> activeReservations = new ConcurrentHashMap<>();

    @Override
    public int dailyQuizQuota(UUID userId) {
        return billingService.hasActivePremium(userId) ? 0 : properties.getFreeTier().getDailyQuiz();
    }

    @Override
    public int dailyChatMessageQuota(UUID userId) {
        return billingService.hasActivePremium(userId) ? 0 : properties.getFreeTier().getDailyChatMessages();
    }

    @Override
    public int materialUploadQuota(UUID userId) {
        return billingService.hasActivePremium(userId) ? 0 : properties.getFreeTier().getMaterialUploads();
    }

    @Override
    public long materialUploadMaxBytes(UUID userId) {
        return billingService.hasActivePremium(userId)
                ? properties.getFreeTier().getMaterialUploadMaxBytesPremium()
                : properties.getFreeTier().getMaterialUploadMaxBytesFree();
    }

    @Override
    public boolean isPremiumFeatureAllowed(UUID userId, PremiumFeature feature) {
        return billingService.hasActivePremium(userId);
    }

    @Override
    public int dailyIlmTokenAllowance(UUID userId) {
        return billingService.hasActivePremium(userId)
                ? quotaProperties.getPremiumDailyIlmTokens()
                : quotaProperties.getFreeDailyIlmTokens();
    }

    @Override
    public int remainingIlmTokensToday(UUID userId) {
        synchronized (lockFor(userId)) {
            return currentBucket(userId).remaining();
        }
    }

    @Override
    public boolean canSpend(UUID userId, int ilmTokens) {
        if (ilmTokens <= 0) {
            return true;
        }
        synchronized (lockFor(userId)) {
            return currentBucket(userId).remaining() >= ilmTokens;
        }
    }

    @Override
    public IlmTokenReservation reserve(UUID userId, int estimatedIlmTokens) {
        int normalized = Math.max(estimatedIlmTokens, 0);
        synchronized (lockFor(userId)) {
            UserDailyIlmTokenBucket bucket = currentBucket(userId);
            if (bucket.remaining() < normalized) {
                throw new IllegalStateException(
                        "ilm-token reservation exceeds remaining budget for user " + userId);
            }
            bucket.reserve(normalized);
            IlmTokenReservation reservation = new IlmTokenReservation(
                    UUID.randomUUID(), userId, bucket.getDateLocal(), normalized);
            activeReservations.put(reservation.getReservationId(), reservation);
            log.debug("quota.reserve user={} estimate={} remaining={}",
                    userId, normalized, bucket.remaining());
            return reservation;
        }
    }

    @Override
    public void commit(IlmTokenReservation reservation, int actualIlmTokensSpent) {
        if (reservation == null) {
            return;
        }
        int actual = Math.max(actualIlmTokensSpent, 0);
        UUID userId = reservation.getUserId();
        synchronized (lockFor(userId)) {
            UserDailyIlmTokenBucket bucket = bucketFor(userId, reservation.getDateLocal());
            bucket.commit(reservation.getEstimatedIlmTokens(), actual);
            activeReservations.remove(reservation.getReservationId());
            log.debug("quota.commit user={} actual={} estimate={} remaining={}",
                    userId, actual, reservation.getEstimatedIlmTokens(), bucket.remaining());
        }
    }

    @Override
    public void refund(IlmTokenReservation reservation) {
        if (reservation == null) {
            return;
        }
        UUID userId = reservation.getUserId();
        synchronized (lockFor(userId)) {
            UserDailyIlmTokenBucket bucket = bucketFor(userId, reservation.getDateLocal());
            bucket.refund(reservation.getEstimatedIlmTokens());
            activeReservations.remove(reservation.getReservationId());
            log.debug("quota.refund user={} estimate={} remaining={}",
                    userId, reservation.getEstimatedIlmTokens(), bucket.remaining());
        }
    }

    private UserDailyIlmTokenBucket currentBucket(UUID userId) {
        return bucketFor(userId, LocalDate.now(clock.withZone(ZoneOffset.UTC)));
    }

    private UserDailyIlmTokenBucket bucketFor(UUID userId, LocalDate dateLocal) {
        UserDailyIlmTokenBucket bucket = buckets.get(userId);
        if (bucket == null || !bucket.getDateLocal().equals(dateLocal)) {
            bucket = new UserDailyIlmTokenBucket(dateLocal, dailyIlmTokenAllowance(userId), 0, 0);
            buckets.put(userId, bucket);
        }
        return bucket;
    }

    private Object lockFor(UUID userId) {
        return userId.toString().intern();
    }
}
