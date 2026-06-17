package org.aiincubator.ilmai.profiles;

import org.aiincubator.ilmai.common.i18n.SupportedLocale;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProfilesApi {

    List<UUID> findAllUserIds();

    List<UUID> findUserIdsWithDailyReminder();

    ProfileDto require(UUID userId);

    Optional<ProfileDto> find(UUID userId);

    void touchActivity(UUID userId);

    void incrementSessionsCount(UUID userId);

    void incrementQuizCount(UUID userId);

    ProfileDto setLearningGoal(UUID userId, String goal);

    ProfileDto setTargetDate(UUID userId, LocalDate targetDate);

    ProfileDto updateGoal(UUID userId, String goal, LocalDate targetDate);

    ProfileDto setPreferredLanguage(UUID userId, SupportedLocale locale);
}
