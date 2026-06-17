package org.aiincubator.ilmai.digest.service;

import lombok.RequiredArgsConstructor;
import org.aiincubator.ilmai.digest.DigestApi;
import org.aiincubator.ilmai.digest.WeeklyDigestDto;
import org.aiincubator.ilmai.digest.domain.WeeklyDigestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DefaultDigestApi implements DigestApi {

    private final WeeklyDigestRepository weeklyDigests;
    private final DigestMapper digestMapper;

    @Override
    @Transactional(readOnly = true)
    public Optional<WeeklyDigestDto> getLatestForUser(UUID userId) {
        return weeklyDigests.findFirstByUserIdOrderByGeneratedAtDesc(userId).map(digestMapper::toDto);
    }
}
