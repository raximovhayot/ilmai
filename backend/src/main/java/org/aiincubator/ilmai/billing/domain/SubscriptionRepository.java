package org.aiincubator.ilmai.billing.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    Optional<Subscription> findFirstByUserIdAndStatusOrderByCreatedAtDesc(UUID userId, SubscriptionStatus status);

    Optional<Subscription> findByProviderAndExternalId(PaymentProviderKind provider, String externalId);

    List<Subscription> findAllByUserIdOrderByCreatedAtDesc(UUID userId);
}
