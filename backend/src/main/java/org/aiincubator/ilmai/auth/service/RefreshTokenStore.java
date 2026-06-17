package org.aiincubator.ilmai.auth.service;

import lombok.RequiredArgsConstructor;
import org.aiincubator.ilmai.auth.domain.RefreshToken;
import org.aiincubator.ilmai.auth.domain.RefreshTokenRepository;
import org.aiincubator.ilmai.auth.domain.RefreshTokenStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class RefreshTokenStore {

    public enum ConsumeResult {
        SUCCESS,
        REUSED,
        UNKNOWN
    }

    private final RefreshTokenRepository repository;

    @Transactional
    public void recordIssued(String jti, UUID userId, UUID familyId, Instant expiresAt) {
        if (!expiresAt.isAfter(Instant.now())) return;
        RefreshToken token = new RefreshToken();
        token.setJti(jti);
        token.setFamilyId(familyId);
        token.setUserId(userId);
        token.setStatus(RefreshTokenStatus.ACTIVE);
        token.setExpiresAt(OffsetDateTime.ofInstant(expiresAt, ZoneOffset.UTC));
        repository.save(token);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ConsumeResult consume(String jti, UUID familyId, Instant expiresAt) {
        OffsetDateTime now = OffsetDateTime.now();
        boolean wonRace = repository.consumeIfActive(jti, now) == 1;
        if (wonRace) {
            return ConsumeResult.SUCCESS;
        }
        if (repository.existsByJtiAndStatusAndExpiresAtAfter(jti, RefreshTokenStatus.CONSUMED, now)) {
            return ConsumeResult.REUSED;
        }
        return ConsumeResult.UNKNOWN;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revokeFamily(UUID familyId) {
        repository.revokeFamily(familyId, OffsetDateTime.now());
    }

    @Transactional(readOnly = true)
    public boolean isFamilyRevoked(UUID familyId) {
        return repository.existsByFamilyIdAndStatus(familyId, RefreshTokenStatus.REVOKED);
    }

    @Transactional
    public boolean revokeActive(String jti) {
        return repository.deleteActiveByJti(jti, OffsetDateTime.now()) == 1;
    }
}
