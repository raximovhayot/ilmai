package org.aiincubator.ilmai.profiles;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.aiincubator.ilmai.common.i18n.SupportedLocale;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@AllArgsConstructor
public final class ProfileDto {

    private final UUID userId;
    private final SupportedLocale locale;
    private final String timezone;
    private final String goal;
    private final LocalDate targetDate;
    private final LocalTime dailyReminder;
    private final Integer dailyStudyMinutes;
    private final int sessionsCount;
    private final int quizCount;
    private final int streakDays;
    private final OffsetDateTime lastActiveAt;
}
