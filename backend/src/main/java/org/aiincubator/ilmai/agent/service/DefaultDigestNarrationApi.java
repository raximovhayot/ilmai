package org.aiincubator.ilmai.agent.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aiincubator.ilmai.agent.DigestNarration;
import org.aiincubator.ilmai.agent.DigestNarrationApi;
import org.aiincubator.ilmai.agent.DigestNarrationInput;
import org.aiincubator.ilmai.common.quota.IlmTokenReservation;
import org.aiincubator.ilmai.common.quota.QuotaService;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DefaultDigestNarrationApi implements DigestNarrationApi {

    static final int DIGEST_NARRATION_ESTIMATE_ILM_TOKENS = 5;

    private final DigestNarrator digestNarrator;
    private final QuotaService quotaService;

    @Override
    public Optional<DigestNarration> narrate(UUID userId, DigestNarrationInput input) {
        if (userId == null || input == null || !digestNarrator.isAvailable()) {
            return Optional.empty();
        }
        if (!quotaService.canSpend(userId, DIGEST_NARRATION_ESTIMATE_ILM_TOKENS)) {
            log.debug("digest narration skipped (quota) user={}", userId);
            return Optional.empty();
        }
        IlmTokenReservation reservation = quotaService.reserve(userId, DIGEST_NARRATION_ESTIMATE_ILM_TOKENS);
        boolean committed = false;
        try {
            DigestNarrationDraft draft = digestNarrator.narrate(input);
            if (draft == null) {
                return Optional.empty();
            }
            quotaService.commit(reservation, draft.getIlmTokenCost());
            committed = true;
            return Optional.of(new DigestNarration(draft.getWhereYouStand(), draft.getFocusNextWeek()));
        } finally {
            if (!committed) {
                quotaService.refund(reservation);
            }
        }
    }
}
