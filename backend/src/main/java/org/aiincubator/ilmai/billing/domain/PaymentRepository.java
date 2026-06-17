package org.aiincubator.ilmai.billing.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByProviderAndExternalId(PaymentProviderKind provider, String externalId);

    List<Payment> findAllByUserIdOrderByOccurredAtDesc(UUID userId);
}
