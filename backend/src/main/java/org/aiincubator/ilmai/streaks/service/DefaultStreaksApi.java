package org.aiincubator.ilmai.streaks.service;

import lombok.RequiredArgsConstructor;
import org.aiincubator.ilmai.streaks.StreakDto;
import org.aiincubator.ilmai.streaks.StreaksApi;
import org.aiincubator.ilmai.streaks.domain.StreakActivityDayRepository;
import org.aiincubator.ilmai.streaks.domain.StreakRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DefaultStreaksApi implements StreaksApi {

    private final StreakRepository streaks;
    private final StreakActivityDayRepository activityDays;
    private final StreaksApiMapper streaksApiMapper;

    @Override
    @Transactional(readOnly = true)
    public StreakDto getStreak(UUID userId) {
        return streaks.findById(userId)
                .map(streaksApiMapper::toDto)
                .orElseGet(() -> new StreakDto(userId, 0, 0, null, null));
    }

    @Override
    @Transactional(readOnly = true)
    public int countActivityDays(UUID userId) {
        return (int) activityDays.countByUserId(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public int countActivityDaysSince(UUID userId, LocalDate from) {
        return (int) activityDays.countByUserIdAndActivityDateGreaterThanEqual(userId, from);
    }
}
