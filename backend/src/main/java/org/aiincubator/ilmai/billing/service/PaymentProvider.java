package org.aiincubator.ilmai.billing.service;

import java.util.UUID;
import org.aiincubator.ilmai.billing.domain.PaymentProviderKind;
import org.aiincubator.ilmai.billing.domain.SubscriptionPlan;

import java.util.Map;

public interface PaymentProvider {

    PaymentProviderKind kind();

    CheckoutSession createCheckout(UUID userId, SubscriptionPlan plan);

    WebhookOutcome parseWebhook(Map<String, Object> payload, String signature);

    default boolean autoActivates() {
        return false;
    }
}
