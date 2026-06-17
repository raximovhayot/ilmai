package org.aiincubator.ilmai.auth.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    @Modifying
    @Query("""
            update RefreshToken t
            set t.status = org.aiincubator.ilmai.auth.domain.RefreshTokenStatus.CONSUMED, t.updatedAt = :now
            where t.jti = :jti
              and t.status = org.aiincubator.ilmai.auth.domain.RefreshTokenStatus.ACTIVE
              and t.expiresAt > :now
            """)
    int consumeIfActive(@Param("jti") String jti, @Param("now") OffsetDateTime now);

    boolean existsByJtiAndStatusAndExpiresAtAfter(String jti, RefreshTokenStatus status, OffsetDateTime now);

    @Modifying
    @Query("""
            update RefreshToken t
            set t.status = org.aiincubator.ilmai.auth.domain.RefreshTokenStatus.REVOKED,
                t.revokedAt = :now,
                t.updatedAt = :now
            where t.familyId = :familyId
              and t.status <> org.aiincubator.ilmai.auth.domain.RefreshTokenStatus.REVOKED
            """)
    int revokeFamily(@Param("familyId") UUID familyId, @Param("now") OffsetDateTime now);

    boolean existsByFamilyIdAndStatus(UUID familyId, RefreshTokenStatus status);

    @Modifying
    @Query("""
            delete from RefreshToken t
            where t.jti = :jti
              and t.status = org.aiincubator.ilmai.auth.domain.RefreshTokenStatus.ACTIVE
              and t.expiresAt > :now
            """)
    int deleteActiveByJti(@Param("jti") String jti, @Param("now") OffsetDateTime now);

    long deleteByStatusNotAndExpiresAtBefore(RefreshTokenStatus status, OffsetDateTime cutoff);

    long deleteByStatusAndRevokedAtBefore(RefreshTokenStatus status, OffsetDateTime cutoff);
}
