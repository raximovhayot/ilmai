package org.aiincubator.ilmai.billing.service;

import org.aiincubator.ilmai.billing.config.BillingProperties;
import org.aiincubator.ilmai.common.quota.IlmTokenQuotaProperties;
import org.aiincubator.ilmai.common.quota.IlmTokenReservation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BillingQuotaServiceIlmTokenLedgerTest {

    private BillingService billingService;
    private BillingProperties properties;
    private IlmTokenQuotaProperties quotaProperties;
    private BillingQuotaService quota;

    @BeforeEach
    void setUp() {
        billingService = mock(BillingService.class);
        properties = new BillingProperties();
        quotaProperties = new IlmTokenQuotaProperties();
        Clock clock = Clock.fixed(Instant.parse("2026-05-26T12:00:00Z"), ZoneOffset.UTC);
        quota = new BillingQuotaService(properties, quotaProperties, billingService, clock);
    }

    @Test
    void freeUserHasFreeTierAllowance() {
        UUID userId = UUID.randomUUID();
        when(billingService.hasActivePremium(userId)).thenReturn(false);

        assertThat(quota.dailyIlmTokenAllowance(userId))
                .isEqualTo(quotaProperties.getFreeDailyIlmTokens());
        assertThat(quota.remainingIlmTokensToday(userId))
                .isEqualTo(quotaProperties.getFreeDailyIlmTokens());
    }

    @Test
    void premiumUserHasPremiumTierAllowance() {
        UUID userId = UUID.randomUUID();
        when(billingService.hasActivePremium(userId)).thenReturn(true);

        assertThat(quota.dailyIlmTokenAllowance(userId))
                .isEqualTo(quotaProperties.getPremiumDailyIlmTokens());
    }

    @Test
    void canSpendIsFalseWhenAllowanceIsZero() {
        UUID userId = UUID.randomUUID();
        when(billingService.hasActivePremium(userId)).thenReturn(false);
        quotaProperties.setFreeDailyIlmTokens(0);

        assertThat(quota.canSpend(userId, 1)).isFalse();
        assertThat(quota.canSpend(userId, 0)).isTrue();
    }

    @Test
    void reserveDecrementsRemainingAndCommitConvertsToSpent() {
        UUID userId = UUID.randomUUID();
        when(billingService.hasActivePremium(userId)).thenReturn(false);
        quotaProperties.setFreeDailyIlmTokens(50);

        IlmTokenReservation reservation = quota.reserve(userId, 5);
        assertThat(reservation.getEstimatedIlmTokens()).isEqualTo(5);
        assertThat(quota.remainingIlmTokensToday(userId)).isEqualTo(45);

        quota.commit(reservation, 3);
        assertThat(quota.remainingIlmTokensToday(userId)).isEqualTo(47);
    }

    @Test
    void commitClampsNegativeActualToZero() {
        UUID userId = UUID.randomUUID();
        when(billingService.hasActivePremium(userId)).thenReturn(false);
        quotaProperties.setFreeDailyIlmTokens(50);

        IlmTokenReservation reservation = quota.reserve(userId, 5);
        quota.commit(reservation, -10);
        assertThat(quota.remainingIlmTokensToday(userId)).isEqualTo(50);
    }

    @Test
    void refundReturnsReservedTokens() {
        UUID userId = UUID.randomUUID();
        when(billingService.hasActivePremium(userId)).thenReturn(false);
        quotaProperties.setFreeDailyIlmTokens(50);

        IlmTokenReservation reservation = quota.reserve(userId, 5);
        assertThat(quota.remainingIlmTokensToday(userId)).isEqualTo(45);

        quota.refund(reservation);
        assertThat(quota.remainingIlmTokensToday(userId)).isEqualTo(50);
    }

    @Test
    void reserveBeyondRemainingThrows() {
        UUID userId = UUID.randomUUID();
        when(billingService.hasActivePremium(userId)).thenReturn(false);
        quotaProperties.setFreeDailyIlmTokens(3);

        assertThatThrownBy(() -> quota.reserve(userId, 10))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void commitOnNullReservationIsNoOp() {
        quota.commit(null, 5);
        quota.refund(null);
    }

    @Test
    void newDayResetsBucket() {
        UUID userId = UUID.randomUUID();
        when(billingService.hasActivePremium(userId)).thenReturn(false);
        quotaProperties.setFreeDailyIlmTokens(10);

        Clock day1 = Clock.fixed(Instant.parse("2026-05-26T12:00:00Z"), ZoneOffset.UTC);
        Clock day2 = Clock.fixed(Instant.parse("2026-05-27T12:00:00Z"), ZoneOffset.UTC);

        BillingQuotaService quotaDay1 = new BillingQuotaService(properties, quotaProperties, billingService, day1);
        IlmTokenReservation r = quotaDay1.reserve(userId, 7);
        quotaDay1.commit(r, 7);
        assertThat(quotaDay1.remainingIlmTokensToday(userId)).isEqualTo(3);

        BillingQuotaService quotaDay2 = new BillingQuotaService(properties, quotaProperties, billingService, day2);
        assertThat(quotaDay2.remainingIlmTokensToday(userId)).isEqualTo(10);
    }
}
