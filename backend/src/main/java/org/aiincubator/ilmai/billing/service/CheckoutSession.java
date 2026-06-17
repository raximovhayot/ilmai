package org.aiincubator.ilmai.billing.service;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;
import org.aiincubator.ilmai.billing.domain.PaymentProviderKind;

@Getter
@AllArgsConstructor
@ToString
public class CheckoutSession {

    private final PaymentProviderKind provider;
    private final String externalId;
    private final String redirectUrl;
}
