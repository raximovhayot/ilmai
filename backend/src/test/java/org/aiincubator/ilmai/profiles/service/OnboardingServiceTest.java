package org.aiincubator.ilmai.profiles.service;

import org.aiincubator.ilmai.common.CurrentUser;
import org.aiincubator.ilmai.profiles.domain.Profile;
import org.aiincubator.ilmai.profiles.domain.ProfileRepository;
import org.aiincubator.ilmai.profiles.payload.OnboardingRequest;
import org.aiincubator.ilmai.profiles.payload.OnboardingResponse;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OnboardingServiceTest {

    private final UUID userId = UUID.randomUUID();
    private final CurrentUser currentUser = new CurrentUser(userId);

    private final ProfileRepository profiles = mock(ProfileRepository.class);
    private final OnboardingService service = new OnboardingService(profiles, new OnboardingMapperImpl());

    @Test
    void submitPersistsProvidedFields() {
        Profile profile = profileFor(userId);
        when(profiles.findById(userId)).thenReturn(Optional.of(profile));

        LocalDate target = LocalDate.now().plusDays(60);
        OnboardingResponse response = service.submit(currentUser,
                new OnboardingRequest("Pass IELTS", target, 45, LocalTime.of(19, 0)));

        assertThat(profile.getGoal()).isEqualTo("Pass IELTS");
        assertThat(profile.getTargetDate()).isEqualTo(target);
        assertThat(profile.getDailyStudyMinutes()).isEqualTo(45);
        assertThat(profile.getDailyReminder()).isEqualTo(LocalTime.of(19, 0));
        assertThat(response.getGoal()).isEqualTo("Pass IELTS");
        assertThat(response.getDailyStudyMinutes()).isEqualTo(45);
        assertThat(response.isTelegramLinked()).isFalse();
    }

    @Test
    void submitWithEmptyRequestLeavesExistingValuesUnchanged() {
        Profile profile = profileFor(userId);
        profile.setGoal("Existing goal");
        profile.setDailyStudyMinutes(30);
        when(profiles.findById(userId)).thenReturn(Optional.of(profile));

        OnboardingResponse response = service.submit(currentUser, new OnboardingRequest());

        assertThat(profile.getGoal()).isEqualTo("Existing goal");
        assertThat(profile.getDailyStudyMinutes()).isEqualTo(30);
        assertThat(response.getGoal()).isEqualTo("Existing goal");
    }

    @Test
    void submitRejectsTargetDateInThePast() {
        Profile profile = profileFor(userId);
        when(profiles.findById(userId)).thenReturn(Optional.of(profile));

        OnboardingRequest req = new OnboardingRequest(null, LocalDate.now().minusDays(1), null, null);
        assertThatThrownBy(() -> service.submit(currentUser, req))
                .isInstanceOf(ProfileException.class);
    }

    @Test
    void getReturnsCurrentProfileValuesWithTelegramPlaceholder() {
        Profile profile = profileFor(userId);
        profile.setGoal("Learn SQL");
        profile.setDailyStudyMinutes(20);
        when(profiles.findById(userId)).thenReturn(Optional.of(profile));

        OnboardingResponse response = service.get(currentUser);

        assertThat(response.getGoal()).isEqualTo("Learn SQL");
        assertThat(response.getDailyStudyMinutes()).isEqualTo(20);
        assertThat(response.isTelegramLinked()).isFalse();
    }

    @Test
    void requiresProfileToExist() {
        when(profiles.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(currentUser)).isInstanceOf(ProfileException.class);
    }

    private Profile profileFor(UUID userId) {
        Profile profile = new Profile();
        profile.setUserId(userId);
        return profile;
    }
}
