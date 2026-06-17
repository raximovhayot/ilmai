package org.aiincubator.ilmai.billing.service;

import org.aiincubator.ilmai.billing.domain.Payment;
import org.aiincubator.ilmai.billing.domain.Subscription;
import org.aiincubator.ilmai.billing.payload.CheckoutSessionResponse;
import org.aiincubator.ilmai.billing.payload.PaymentResponse;
import org.aiincubator.ilmai.billing.payload.SubscriptionResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface BillingMapper {

    @Mapping(target = "plan", expression = "java(subscription.getPlan() != null ? subscription.getPlan().name() : null)")
    @Mapping(target = "status", expression = "java(subscription.getStatus() != null ? subscription.getStatus().name() : null)")
    @Mapping(target = "provider", expression = "java(subscription.getProvider() != null ? subscription.getProvider().name() : null)")
    SubscriptionResponse toResponse(Subscription subscription);

    @Mapping(target = "provider", expression = "java(payment.getProvider() != null ? payment.getProvider().name() : null)")
    @Mapping(target = "status", expression = "java(payment.getStatus() != null ? payment.getStatus().name() : null)")
    PaymentResponse toResponse(Payment payment);

    @Mapping(target = "provider", expression = "java(session.getProvider() != null ? session.getProvider().name() : null)")
    CheckoutSessionResponse toResponse(CheckoutSession session);
}
