package org.aiincubator.ilmai.profiles.service;

import lombok.RequiredArgsConstructor;
import org.aiincubator.ilmai.common.CurrentUser;
import org.aiincubator.ilmai.common.i18n.SupportedLocale;
import org.aiincubator.ilmai.profiles.domain.Profile;
import org.aiincubator.ilmai.profiles.domain.ProfileRepository;
import org.aiincubator.ilmai.profiles.payload.ProfileResponse;
import org.aiincubator.ilmai.profiles.payload.UpdateProfileRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProfileService {

    private final ProfileRepository profiles;
    private final ProfileMapper profileMapper;

    @Transactional
    public Profile createForUser(UUID userId, SupportedLocale locale) {
        Profile profile = new Profile();
        profile.setUserId(userId);
        profile.setLocale(locale != null ? locale : SupportedLocale.DEFAULT);
        profile.setTimezone("UTC");
        return profiles.save(profile);
    }

    @Transactional(readOnly = true)
    public ProfileResponse get(CurrentUser currentUser) {
        return profileMapper.toResponse(require(currentUser.getUserId()));
    }

    @Transactional
    public ProfileResponse update(CurrentUser currentUser, UpdateProfileRequest req) {
        Profile profile = require(currentUser.getUserId());
        if (req.getLocale() != null) {
            SupportedLocale parsed = SupportedLocale.fromLanguageTag(req.getLocale())
                    .orElseThrow(() -> new ProfileException(ProfileException.Reason.PROFILE_INVALID_LOCALE, req.getLocale()));
            profile.setLocale(parsed);
        }
        if (req.getTimezone() != null) {
            try {
                ZoneId.of(req.getTimezone());
            } catch (Exception ex) {
                throw new ProfileException(ProfileException.Reason.PROFILE_INVALID_TIMEZONE, req.getTimezone());
            }
            profile.setTimezone(req.getTimezone());
        }
        if (req.getGoal() != null) {
            String trimmed = req.getGoal().trim();
            profile.setGoal(trimmed.isEmpty() ? null : trimmed);
        }
        if (req.getTargetDate() != null) {
            if (req.getTargetDate().isBefore(LocalDate.now())) {
                throw new ProfileException(ProfileException.Reason.PROFILE_INVALID_TARGET_DATE);
            }
            profile.setTargetDate(req.getTargetDate());
        }
        if (req.getDailyReminder() != null) {
            profile.setDailyReminder(req.getDailyReminder());
        }
        if (req.getDailyStudyMinutes() != null) {
            profile.setDailyStudyMinutes(req.getDailyStudyMinutes());
        }
        return profileMapper.toResponse(profile);
    }

    private Profile require(UUID userId) {
        return profiles.findById(userId)
                .orElseThrow(() -> new ProfileException(ProfileException.Reason.PROFILE_NOT_FOUND));
    }
}
