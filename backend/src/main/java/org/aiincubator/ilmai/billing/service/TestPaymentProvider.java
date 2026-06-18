package org.aiincubator.ilmai.billing.service;

import org.aiincubator.ilmai.billing.domain.PaymentProviderKind;
import org.aiincubator.ilmai.billing.domain.PaymentStatus;
import org.aiincubator.ilmai.billing.domain.SubscriptionPlan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Component
@ConditionalOnProperty(prefix = "billing.test-provider", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TestPaymentProvider implements PaymentProvider {

    private static final String CURRENCY = "USD";

    @Override
    public PaymentProviderKind kind() {
        return PaymentProviderKind.TEST;
    }

    @Override
    public boolean autoActivates() {
        return true;
    }

    @Override
    public CheckoutSession createCheckout(UUID userId, SubscriptionPlan plan) {
        return new CheckoutSession(PaymentProviderKind.TEST, "test_sub_" + UUID.randomUUID(), null);
    }

    @Override
    public WebhookOutcome parseWebhook(Map<String, Object> payload, String signature) {
        SubscriptionPlan plan = parsePlan(payload.get("plan"));
        UUID userId = parseUserId(payload.get("userId"));
        String subscriptionId = payload.get("subscriptionId") == null
                ? null
                : String.valueOf(payload.get("subscriptionId"));
        OffsetDateTime start = OffsetDateTime.now();
        OffsetDateTime end = plan == SubscriptionPlan.PREMIUM_YEARLY ? start.plusYears(1) : start.plusMonths(1);
        long amountMinor = plan == SubscriptionPlan.PREMIUM_YEARLY ? 4990L : 499L;
        return new WebhookOutcome(PaymentProviderKind.TEST, subscriptionId, "test_pay_" + UUID.randomUUID(),
                userId, PaymentStatus.SUCCEEDED, amountMinor, CURRENCY, start, end, payload);
    }

    private SubscriptionPlan parsePlan(Object value) {
        if (value == null) {
            return SubscriptionPlan.PREMIUM_MONTHLY;
        }
        try {
            return SubscriptionPlan.valueOf(String.valueOf(value).trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return SubscriptionPlan.PREMIUM_MONTHLY;
        }
    }

    private UUID parseUserId(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(String.valueOf(value).trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
