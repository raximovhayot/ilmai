package org.aiincubator.ilmai.billing.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aiincubator.ilmai.common.CurrentUser;
import org.aiincubator.ilmai.billing.domain.Payment;
import org.aiincubator.ilmai.billing.domain.PaymentProviderKind;
import org.aiincubator.ilmai.billing.domain.PaymentRepository;
import org.aiincubator.ilmai.billing.domain.PaymentStatus;
import org.aiincubator.ilmai.billing.domain.Subscription;
import org.aiincubator.ilmai.billing.domain.SubscriptionPlan;
import org.aiincubator.ilmai.billing.domain.SubscriptionRepository;
import org.aiincubator.ilmai.billing.domain.SubscriptionStatus;
import org.aiincubator.ilmai.billing.payload.CheckoutSessionResponse;
import org.aiincubator.ilmai.billing.payload.PaymentResponse;
import org.aiincubator.ilmai.billing.payload.SubscriptionResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class BillingService {

    private final SubscriptionRepository subscriptions;
    private final PaymentRepository payments;
    private final List<PaymentProvider> providers;
    private final BillingMapper billingMapper;

    @Transactional
    public CheckoutSessionResponse startCheckout(CurrentUser currentUser, String planValue, String providerValue) {
        SubscriptionPlan plan = parsePlan(planValue);
        PaymentProviderKind kind = parseProvider(providerValue);
        UUID userId = currentUser.getUserId();
        PaymentProvider provider = providerFor(kind);
        CheckoutSession session = provider.createCheckout(userId, plan);

        Subscription subscription = new Subscription();
        subscription.setUserId(userId);
        subscription.setPlan(plan);
        subscription.setProvider(kind);
        subscription.setStatus(SubscriptionStatus.PENDING);
        subscription.setExternalId(session.getExternalId());
        subscriptions.save(subscription);

        if (provider.autoActivates()) {
            Map<String, Object> payload = Map.of(
                    "userId", userId.toString(),
                    "subscriptionId", session.getExternalId(),
                    "plan", plan.name());
            applyOutcome(userId, kind, provider.parseWebhook(payload, null));
        }
        return billingMapper.toResponse(session);
    }

    @Transactional
    public SubscriptionResponse cancel(CurrentUser currentUser) {
        Subscription subscription = subscriptions
                .findFirstByUserIdAndStatusOrderByCreatedAtDesc(currentUser.getUserId(), SubscriptionStatus.ACTIVE)
                .orElseThrow(() -> new BillingException(BillingException.Reason.SUBSCRIPTION_NOT_FOUND));
        subscription.setCancelAtPeriodEnd(true);
        return billingMapper.toResponse(subscription);
    }

    @Transactional(readOnly = true)
    public SubscriptionResponse activeSubscription(CurrentUser currentUser) {
        return subscriptions
                .findFirstByUserIdAndStatusOrderByCreatedAtDesc(currentUser.getUserId(), SubscriptionStatus.ACTIVE)
                .map(billingMapper::toResponse)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public List<SubscriptionResponse> history(CurrentUser currentUser) {
        return subscriptions.findAllByUserIdOrderByCreatedAtDesc(currentUser.getUserId()).stream()
                .map(billingMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> payments(CurrentUser currentUser) {
        return payments.findAllByUserIdOrderByOccurredAtDesc(currentUser.getUserId()).stream()
                .map(billingMapper::toResponse)
                .toList();
    }

    @Transactional
    public void handleWebhook(String providerName, Map<String, Object> payload, String signature) {
        PaymentProviderKind kind = parseProvider(providerName);
        PaymentProvider provider = providerFor(kind);
        WebhookOutcome outcome = provider.parseWebhook(payload, signature);

        UUID userId = outcome.getUserId();
        if (userId == null && outcome.getExternalSubscriptionId() != null) {
            userId = subscriptions
                    .findByProviderAndExternalId(kind, outcome.getExternalSubscriptionId())
                    .map(Subscription::getUserId)
                    .orElse(null);
        }
        if (userId == null) {
            log.warn("billing: webhook from {} has no resolvable user; ignored", kind);
            return;
        }

        applyOutcome(userId, kind, outcome);
    }

    private void applyOutcome(UUID userId, PaymentProviderKind kind, WebhookOutcome outcome) {
        Subscription subscription = resolveSubscription(userId, kind, outcome);
        Payment payment = persistPayment(userId, subscription, outcome);
        if (subscription != null && outcome.getPaymentStatus() == PaymentStatus.SUCCEEDED) {
            subscription.setStatus(SubscriptionStatus.ACTIVE);
            subscription.setCurrentPeriodStart(outcome.getCurrentPeriodStart() != null
                    ? outcome.getCurrentPeriodStart()
                    : OffsetDateTime.now());
            subscription.setCurrentPeriodEnd(outcome.getCurrentPeriodEnd() != null
                    ? outcome.getCurrentPeriodEnd()
                    : OffsetDateTime.now().plusMonths(1));
            subscriptions.save(subscription);
        }
        if (subscription != null && outcome.getPaymentStatus() == PaymentStatus.REFUNDED) {
            subscription.setStatus(SubscriptionStatus.CANCELED);
            subscriptions.save(subscription);
        }
        log.info("billing: outcome processed {} status={} subscription={}",
                payment.getId(), outcome.getPaymentStatus(),
                subscription == null ? "(none)" : subscription.getId());
    }

    public boolean hasActivePremium(UUID userId) {
        return subscriptions
                .findFirstByUserIdAndStatusOrderByCreatedAtDesc(userId, SubscriptionStatus.ACTIVE)
                .filter(s -> s.getPlan() != SubscriptionPlan.FREE)
                .filter(s -> s.getCurrentPeriodEnd() == null || s.getCurrentPeriodEnd().isAfter(OffsetDateTime.now()))
                .isPresent();
    }

    private Subscription resolveSubscription(UUID userId, PaymentProviderKind kind, WebhookOutcome outcome) {
        if (outcome.getExternalSubscriptionId() != null) {
            return subscriptions.findByProviderAndExternalId(kind, outcome.getExternalSubscriptionId())
                    .orElseGet(() -> subscriptions
                            .findAllByUserIdOrderByCreatedAtDesc(userId).stream()
                            .filter(s -> s.getProvider() == kind && s.getStatus() == SubscriptionStatus.PENDING)
                            .max(Comparator.comparing(Subscription::getCreatedAt))
                            .orElse(null));
        }
        return subscriptions.findAllByUserIdOrderByCreatedAtDesc(userId).stream()
                .filter(s -> s.getProvider() == kind)
                .max(Comparator.comparing(Subscription::getCreatedAt))
                .orElse(null);
    }

    private Payment persistPayment(UUID userId, Subscription subscription, WebhookOutcome outcome) {
        if (outcome.getExternalPaymentId() != null) {
            Optional<Payment> existing = payments
                    .findByProviderAndExternalId(outcome.getProvider(), outcome.getExternalPaymentId());
            if (existing.isPresent()) {
                Payment p = existing.get();
                p.setStatus(outcome.getPaymentStatus());
                p.setRawPayload(outcome.getRaw());
                return payments.save(p);
            }
        }
        Payment payment = new Payment();
        payment.setUserId(userId);
        payment.setSubscription(subscription);
        payment.setProvider(outcome.getProvider());
        payment.setExternalId(outcome.getExternalPaymentId());
        payment.setAmountMinor(outcome.getAmountMinor());
        payment.setCurrency(outcome.getCurrency() == null ? "USD" : outcome.getCurrency());
        payment.setStatus(outcome.getPaymentStatus());
        payment.setRawPayload(outcome.getRaw());
        payment.setOccurredAt(OffsetDateTime.now());
        return payments.save(payment);
    }

    private SubscriptionPlan parsePlan(String value) {
        if (value == null || value.isBlank()) {
            throw new BillingException(BillingException.Reason.INVALID_PLAN, value);
        }
        try {
            return SubscriptionPlan.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BillingException(BillingException.Reason.INVALID_PLAN, value);
        }
    }

    private PaymentProviderKind parseProvider(String value) {
        if (value == null || value.isBlank()) {
            throw new BillingException(BillingException.Reason.INVALID_PROVIDER, value);
        }
        try {
            return PaymentProviderKind.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BillingException(BillingException.Reason.INVALID_PROVIDER, value);
        }
    }

    private PaymentProvider providerFor(PaymentProviderKind kind) {
        return providers.stream()
                .filter(p -> p.kind() == kind)
                .findFirst()
                .orElseThrow(() -> new BillingException(BillingException.Reason.INVALID_PROVIDER, kind.name()));
    }
}
