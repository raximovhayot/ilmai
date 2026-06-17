package org.aiincubator.ilmai.billing.service;

import org.aiincubator.ilmai.common.CurrentUser;
import org.aiincubator.ilmai.billing.domain.PaymentProviderKind;
import org.aiincubator.ilmai.billing.domain.PaymentRepository;
import org.aiincubator.ilmai.billing.domain.PaymentStatus;
import org.aiincubator.ilmai.billing.domain.Subscription;
import org.aiincubator.ilmai.billing.domain.SubscriptionPlan;
import org.aiincubator.ilmai.billing.domain.SubscriptionRepository;
import org.aiincubator.ilmai.billing.domain.SubscriptionStatus;
import org.aiincubator.ilmai.billing.payload.CheckoutSessionResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BillingServiceTest {

    @Mock SubscriptionRepository subscriptions;
    @Mock PaymentRepository payments;

    private BillingService newServiceWith(PaymentProvider... providers) {
        BillingMapper mapper = Mappers.getMapper(BillingMapper.class);
        return new BillingService(subscriptions, payments, List.of(providers), mapper);
    }

    @Test
    void startCheckout_rejectsUnknownPlan() {
        BillingService service = newServiceWith();
        UUID userId = UUID.randomUUID();
        assertThatThrownBy(() -> service.startCheckout(new CurrentUser(userId), "GOLD", "STRIPE"))
                .isInstanceOf(BillingException.class)
                .extracting(e -> ((BillingException) e).getReason())
                .isEqualTo(BillingException.Reason.INVALID_PLAN);
    }

    @Test
    void startCheckout_rejectsUnknownProvider() {
        BillingService service = newServiceWith();
        UUID userId = UUID.randomUUID();
        assertThatThrownBy(() -> service.startCheckout(new CurrentUser(userId), "PREMIUM_MONTHLY", "PAYPAL"))
                .isInstanceOf(BillingException.class)
                .extracting(e -> ((BillingException) e).getReason())
                .isEqualTo(BillingException.Reason.INVALID_PROVIDER);
    }

    @Test
    void startCheckout_persistsPendingSubscriptionAndReturnsCheckoutUrl() {
        UUID userId = UUID.randomUUID();
        StubProvider stub = new StubProvider(PaymentProviderKind.STRIPE);
        BillingService service = newServiceWith(stub);
        when(subscriptions.save(any(Subscription.class))).thenAnswer(inv -> inv.getArgument(0));

        CheckoutSessionResponse response = service.startCheckout(
                new CurrentUser(userId), "PREMIUM_MONTHLY", "STRIPE");

        assertThat(response.getProvider()).isEqualTo("STRIPE");
        assertThat(response.getRedirectUrl()).startsWith("https://stub.example.com/");
    }

    @Test
    void hasActivePremium_returnsTrueWhenSubscriptionActiveAndNotExpired() {
        UUID userId = UUID.randomUUID();
        Subscription subscription = new Subscription();
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setPlan(SubscriptionPlan.PREMIUM_MONTHLY);
        subscription.setProvider(PaymentProviderKind.STRIPE);
        subscription.setCurrentPeriodEnd(OffsetDateTime.now().plusDays(10));
        when(subscriptions.findFirstByUserIdAndStatusOrderByCreatedAtDesc(userId, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.of(subscription));

        BillingService service = newServiceWith();
        assertThat(service.hasActivePremium(userId)).isTrue();
    }

    @Test
    void hasActivePremium_returnsFalseWhenNoActiveSubscription() {
        UUID userId = UUID.randomUUID();
        when(subscriptions.findFirstByUserIdAndStatusOrderByCreatedAtDesc(userId, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.empty());

        BillingService service = newServiceWith();
        assertThat(service.hasActivePremium(userId)).isFalse();
    }
}

class StubProvider implements PaymentProvider {

    private final PaymentProviderKind kind;

    StubProvider(PaymentProviderKind kind) {
        this.kind = kind;
    }

    @Override
    public PaymentProviderKind kind() {
        return kind;
    }

    @Override
    public CheckoutSession createCheckout(UUID userId, SubscriptionPlan plan) {
        return new CheckoutSession(kind, "stub_" + UUID.randomUUID(),
                "https://stub.example.com/" + userId + "/" + plan);
    }

    @Override
    public WebhookOutcome parseWebhook(Map<String, Object> payload, String signature) {
        return new WebhookOutcome(kind, null, null, null, PaymentStatus.SUCCEEDED, 0L, "USD",
                OffsetDateTime.now(), OffsetDateTime.now().plusMonths(1), payload);
    }
}
