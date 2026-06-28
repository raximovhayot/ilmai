package org.aiincubator.ilmai.profiles.service;

import lombok.RequiredArgsConstructor;
import org.aiincubator.ilmai.common.CurrentUser;
import org.aiincubator.ilmai.common.i18n.SupportedLocale;
import org.aiincubator.ilmai.profiles.domain.Profile;
import org.aiincubator.ilmai.profiles.domain.ProfileRepository;
import org.aiincubator.ilmai.profiles.payload.ProfileResponse;
import org.aiincubator.ilmai.profiles.payload.UpdateProfileRequest;
import org.aiincubator.ilmai.rooms.RoomGoalDto;
import org.aiincubator.ilmai.rooms.RoomsApi;
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
    private final RoomsApi roomsApi;

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
        UUID userId = currentUser.getUserId();
        ProfileResponse response = profileMapper.toResponse(require(userId));
        applyRoomGoal(response, roomsApi.findPersonalGoalForUser(userId).orElse(null));
        return response;
    }

    @Transactional
    public ProfileResponse update(CurrentUser currentUser, UpdateProfileRequest req) {
        UUID userId = currentUser.getUserId();
        Profile profile = require(userId);
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
        if (req.getTargetDate() != null && req.getTargetDate().isBefore(LocalDate.now())) {
            throw new ProfileException(ProfileException.Reason.PROFILE_INVALID_TARGET_DATE);
        }
        if (req.getDailyReminder() != null) {
            profile.setDailyReminder(req.getDailyReminder());
        }
        RoomGoalDto goal = roomsApi.applyGoalPatch(userId, req.getGoal(), req.getTargetDate(),
                req.getDailyStudyMinutes()).orElse(null);
        ProfileResponse response = profileMapper.toResponse(profile);
        applyRoomGoal(response, goal != null ? goal : roomsApi.findPersonalGoalForUser(userId).orElse(null));
        return response;
    }

    private void applyRoomGoal(ProfileResponse response, RoomGoalDto goal) {
        if (goal == null) {
            return;
        }
        response.setGoal(goal.getGoal());
        response.setTargetDate(goal.getTargetDate());
        response.setDailyStudyMinutes(goal.getDailyStudyMinutes());
    }

    private Profile require(UUID userId) {
        return profiles.findById(userId)
                .orElseThrow(() -> new ProfileException(ProfileException.Reason.PROFILE_NOT_FOUND));
    }
}
