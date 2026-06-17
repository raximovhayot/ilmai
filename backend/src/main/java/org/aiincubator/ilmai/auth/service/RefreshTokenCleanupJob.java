package org.aiincubator.ilmai.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aiincubator.ilmai.auth.config.AuthProperties;
import org.aiincubator.ilmai.auth.domain.RefreshTokenRepository;
import org.aiincubator.ilmai.auth.domain.RefreshTokenStatus;
import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.annotations.Recurring;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class RefreshTokenCleanupJob {

    static final String RECURRING_JOB_ID = "auth-refresh-token-cleanup";

    private final RefreshTokenRepository refreshTokens;
    private final AuthProperties props;

    @Recurring(id = RECURRING_JOB_ID, cron = "0 * * * *")
    @Job(name = "Refresh token cleanup")
    @Transactional
    public void run() {
        OffsetDateTime now = OffsetDateTime.now();
        long expired = refreshTokens.deleteByStatusNotAndExpiresAtBefore(RefreshTokenStatus.REVOKED, now);
        long staleRevoked = refreshTokens.deleteByStatusAndRevokedAtBefore(
                RefreshTokenStatus.REVOKED, now.minus(props.getJwt().getAccessTtl()));
        log.debug("refresh token cleanup removed {} expired and {} stale revoked row(s)", expired, staleRevoked);
    }
}
