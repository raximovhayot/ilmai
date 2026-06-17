package org.aiincubator.ilmai.streaks.service;

import lombok.RequiredArgsConstructor;
import org.aiincubator.ilmai.profiles.ProfileDto;
import org.aiincubator.ilmai.profiles.ProfilesApi;
import org.aiincubator.ilmai.streaks.StreakBrokenEvent;
import org.aiincubator.ilmai.streaks.StreakMilestoneReachedEvent;
import org.aiincubator.ilmai.streaks.domain.Streak;
import org.aiincubator.ilmai.streaks.domain.StreakActivityDay;
import org.aiincubator.ilmai.streaks.domain.StreakActivityDayRepository;
import org.aiincubator.ilmai.streaks.domain.StreakRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StreakService {

    static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Tashkent");
    static final Set<Integer> MILESTONES = Set.of(7, 14, 30, 60, 100);

    private final StreakRepository streaks;
    private final StreakActivityDayRepository activityDays;
    private final ProfilesApi profilesApi;
    private final Clock clock;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void recordActivity(UUID userId, OffsetDateTime occurredAt) {
        if (userId == null || occurredAt == null) {
            return;
        }
        LocalDate localDate = occurredAt.atZoneSameInstant(zoneFor(userId)).toLocalDate();
        if (activityDays.existsByUserIdAndActivityDate(userId, localDate)) {
            return;
        }
        StreakActivityDay day = new StreakActivityDay();
        day.setUserId(userId);
        day.setActivityDate(localDate);
        activityDays.save(day);
    }

    @Transactional
    public void rollover(UUID userId, LocalDate completedDay) {
        if (userId == null || completedDay == null) {
            return;
        }
        Streak streak = streaks.findById(userId).orElseGet(() -> {
            Streak created = new Streak();
            created.setUserId(userId);
            return created;
        });
        boolean active = activityDays.existsByUserIdAndActivityDate(userId, completedDay);
        LocalDate last = streak.getStreakLastDay();

        if (active) {
            if (last != null && !last.isBefore(completedDay)) {
                return;
            }
            if (last != null && last.equals(completedDay.minusDays(1))) {
                streak.setStreakCurrent(streak.getStreakCurrent() + 1);
            } else {
                streak.setStreakCurrent(1);
            }
            streak.setStreakLastDay(completedDay);
            if (streak.getStreakCurrent() > streak.getStreakLongest()) {
                streak.setStreakLongest(streak.getStreakCurrent());
            }
            streaks.save(streak);
            if (MILESTONES.contains(streak.getStreakCurrent())) {
                eventPublisher.publishEvent(
                        new StreakMilestoneReachedEvent(userId, streak.getStreakCurrent()));
            }
        } else if (streak.getStreakCurrent() > 0
                && last != null
                && last.equals(completedDay.minusDays(1))) {
            int brokenStreakLength = streak.getStreakCurrent();
            streak.setStreakCurrent(0);
            streak.setStreakBrokenAt(completedDay);
            streaks.save(streak);
            eventPublisher.publishEvent(
                    new StreakBrokenEvent(userId, completedDay, brokenStreakLength));
        }
    }

    @Transactional
    public void rolloverYesterday(UUID userId) {
        if (userId == null) {
            return;
        }
        LocalDate localToday = LocalDate.ofInstant(clock.instant(), zoneFor(userId));
        rollover(userId, localToday.minusDays(1));
    }

    private ZoneId zoneFor(UUID userId) {
        return profilesApi.find(userId)
                .map(ProfileDto::getTimezone)
                .filter(tz -> tz != null && !tz.isBlank())
                .map(StreakService::parseZone)
                .orElse(DEFAULT_ZONE);
    }

    private static ZoneId parseZone(String tz) {
        try {
            return ZoneId.of(tz);
        } catch (RuntimeException ex) {
            return DEFAULT_ZONE;
        }
    }
}
