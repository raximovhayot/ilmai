package org.aiincubator.ilmai.profiles.service;

import lombok.RequiredArgsConstructor;
import org.aiincubator.ilmai.common.CurrentUser;
import org.aiincubator.ilmai.profiles.domain.Profile;
import org.aiincubator.ilmai.profiles.domain.ProfileRepository;
import org.aiincubator.ilmai.profiles.payload.OnboardingRequest;
import org.aiincubator.ilmai.profiles.payload.OnboardingResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OnboardingService {

    private final ProfileRepository profiles;
    private final OnboardingMapper onboardingMapper;

    @Transactional(readOnly = true)
    public OnboardingResponse get(CurrentUser currentUser) {
        return onboardingMapper.toResponse(require(currentUser.getUserId()));
    }

    @Transactional
    public OnboardingResponse submit(CurrentUser currentUser, OnboardingRequest req) {
        Profile profile = require(currentUser.getUserId());
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
        if (req.getDailyStudyMinutes() != null) {
            profile.setDailyStudyMinutes(req.getDailyStudyMinutes());
        }
        if (req.getDailyReminder() != null) {
            profile.setDailyReminder(req.getDailyReminder());
        }
        return onboardingMapper.toResponse(profile);
    }

    private Profile require(UUID userId) {
        return profiles.findById(userId)
                .orElseThrow(() -> new ProfileException(ProfileException.Reason.PROFILE_NOT_FOUND));
    }
}
