package org.aiincubator.ilmai.profiles.service;

import lombok.RequiredArgsConstructor;
import org.aiincubator.ilmai.common.i18n.SupportedLocale;
import org.aiincubator.ilmai.profiles.GoalUpdatedEvent;
import org.aiincubator.ilmai.profiles.ProfileDto;
import org.aiincubator.ilmai.profiles.ProfilesApi;
import org.aiincubator.ilmai.profiles.domain.Profile;
import org.aiincubator.ilmai.profiles.domain.ProfileRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DefaultProfilesApi implements ProfilesApi {

    private final ProfileRepository profiles;
    private final ProfilesApiMapper profilesApiMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional(readOnly = true)
    public ProfileDto require(UUID userId) {
        Profile profile = profiles.findById(userId)
                .orElseThrow(() -> new ProfileException(ProfileException.Reason.PROFILE_NOT_FOUND));
        return profilesApiMapper.toProfileDto(profile);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProfileDto> find(UUID userId) {
        return profiles.findById(userId).map(profilesApiMapper::toProfileDto);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> findAllUserIds() {
        return profiles.findAllUserIds();
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> findUserIdsWithDailyReminder() {
        return profiles.findUserIdsWithDailyReminder();
    }

    @Override
    @Transactional
    public void touchActivity(UUID userId) {
        profiles.findById(userId).ifPresent(p -> p.setLastActiveAt(OffsetDateTime.now()));
    }

    @Override
    @Transactional
    public void incrementSessionsCount(UUID userId) {
        profiles.findById(userId).ifPresent(p -> p.setSessionsCount(p.getSessionsCount() + 1));
    }

    @Override
    @Transactional
    public void incrementQuizCount(UUID userId) {
        profiles.findById(userId).ifPresent(p -> p.setQuizCount(p.getQuizCount() + 1));
    }

    @Override
    @Transactional
    public ProfileDto setLearningGoal(UUID userId, String goal) {
        Profile profile = profiles.findById(userId)
                .orElseThrow(() -> new ProfileException(ProfileException.Reason.PROFILE_NOT_FOUND));
        if (goal == null) {
            profile.setGoal(null);
        } else {
            String trimmed = goal.trim();
            profile.setGoal(trimmed.isEmpty() ? null : trimmed);
        }
        return profilesApiMapper.toProfileDto(profile);
    }

    @Override
    @Transactional
    public ProfileDto setTargetDate(UUID userId, LocalDate targetDate) {
        Profile profile = profiles.findById(userId)
                .orElseThrow(() -> new ProfileException(ProfileException.Reason.PROFILE_NOT_FOUND));
        if (targetDate != null && targetDate.isBefore(LocalDate.now())) {
            throw new ProfileException(ProfileException.Reason.PROFILE_INVALID_TARGET_DATE);
        }
        profile.setTargetDate(targetDate);
        return profilesApiMapper.toProfileDto(profile);
    }

    @Override
    @Transactional
    public ProfileDto updateGoal(UUID userId, String goal, LocalDate targetDate) {
        Profile profile = profiles.findById(userId)
                .orElseThrow(() -> new ProfileException(ProfileException.Reason.PROFILE_NOT_FOUND));
        if (goal != null) {
            String trimmed = goal.trim();
            profile.setGoal(trimmed.isEmpty() ? null : trimmed);
        }
        if (targetDate != null) {
            if (targetDate.isBefore(LocalDate.now())) {
                throw new ProfileException(ProfileException.Reason.PROFILE_INVALID_TARGET_DATE);
            }
            profile.setTargetDate(targetDate);
        }
        if (goal != null || targetDate != null) {
            eventPublisher.publishEvent(new GoalUpdatedEvent(userId));
        }
        return profilesApiMapper.toProfileDto(profile);
    }

    @Override
    @Transactional
    public ProfileDto setPreferredLanguage(UUID userId, SupportedLocale locale) {
        Profile profile = profiles.findById(userId)
                .orElseThrow(() -> new ProfileException(ProfileException.Reason.PROFILE_NOT_FOUND));
        profile.setLocale(locale != null ? locale : SupportedLocale.DEFAULT);
        return profilesApiMapper.toProfileDto(profile);
    }
}
