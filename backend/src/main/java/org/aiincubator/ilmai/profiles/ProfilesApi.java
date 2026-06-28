package org.aiincubator.ilmai.profiles;

import org.aiincubator.ilmai.common.i18n.SupportedLocale;

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

    ProfileDto setPreferredLanguage(UUID userId, SupportedLocale locale);
}
