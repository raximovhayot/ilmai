package org.aiincubator.ilmai.billing.service;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;
import org.aiincubator.ilmai.billing.domain.PaymentProviderKind;
import org.aiincubator.ilmai.billing.domain.PaymentStatus;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Getter
@AllArgsConstructor
@ToString
public class WebhookOutcome {

    private final PaymentProviderKind provider;
    private final String externalSubscriptionId;
    private final String externalPaymentId;
    private final UUID userId;
    private final PaymentStatus paymentStatus;
    private final long amountMinor;
    private final String currency;
    private final OffsetDateTime currentPeriodStart;
    private final OffsetDateTime currentPeriodEnd;
    private final Map<String, Object> raw;
}
