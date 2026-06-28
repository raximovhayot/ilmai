package org.aiincubator.ilmai.profiles.service;

import lombok.RequiredArgsConstructor;
import org.aiincubator.ilmai.common.CurrentUser;
import org.aiincubator.ilmai.profiles.OnboardingCompletedEvent;
import org.aiincubator.ilmai.profiles.domain.Profile;
import org.aiincubator.ilmai.profiles.domain.ProfileRepository;
import org.aiincubator.ilmai.profiles.payload.OnboardingRequest;
import org.aiincubator.ilmai.profiles.payload.OnboardingResponse;
import org.aiincubator.ilmai.rooms.RoomGoalDto;
import org.aiincubator.ilmai.rooms.RoomsApi;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OnboardingService {

    private final ProfileRepository profiles;
    private final OnboardingMapper onboardingMapper;
    private final RoomsApi roomsApi;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public OnboardingResponse get(CurrentUser currentUser) {
        UUID userId = currentUser.getUserId();
        OnboardingResponse response = onboardingMapper.toResponse(require(userId));
        applyRoomGoal(response, roomsApi.findPersonalGoalForUser(userId).orElse(null));
        return response;
    }

    @Transactional
    public OnboardingResponse submit(CurrentUser currentUser, OnboardingRequest req) {
        UUID userId = currentUser.getUserId();
        Profile profile = require(userId);
        if (req.getTargetDate() != null && req.getTargetDate().isBefore(LocalDate.now())) {
            throw new ProfileException(ProfileException.Reason.PROFILE_INVALID_TARGET_DATE);
        }
        RoomGoalDto goal = roomsApi.applyGoalPatch(userId, req.getGoal(), req.getTargetDate(),
                req.getDailyStudyMinutes()).orElse(null);
        if (req.getDailyReminder() != null) {
            profile.setDailyReminder(req.getDailyReminder());
        }
        boolean onboardingJustCompleted = false;
        if (req.getOnboardingPassed() != null) {
            onboardingJustCompleted = Boolean.TRUE.equals(req.getOnboardingPassed())
                    && !Boolean.TRUE.equals(profile.getOnboardingPassed());
            profile.setOnboardingPassed(req.getOnboardingPassed());
        }
        if (onboardingJustCompleted) {
            eventPublisher.publishEvent(new OnboardingCompletedEvent(userId));
        }
        OnboardingResponse response = onboardingMapper.toResponse(profile);
        applyRoomGoal(response, goal != null ? goal : roomsApi.findPersonalGoalForUser(userId).orElse(null));
        return response;
    }

    private void applyRoomGoal(OnboardingResponse response, RoomGoalDto goal) {
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
