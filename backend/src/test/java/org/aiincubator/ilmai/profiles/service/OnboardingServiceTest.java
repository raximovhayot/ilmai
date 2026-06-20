package org.aiincubator.ilmai.profiles.service;

import org.aiincubator.ilmai.common.CurrentUser;
import org.aiincubator.ilmai.profiles.GoalUpdatedEvent;
import org.aiincubator.ilmai.profiles.OnboardingCompletedEvent;
import org.aiincubator.ilmai.profiles.domain.Profile;
import org.aiincubator.ilmai.profiles.domain.ProfileRepository;
import org.aiincubator.ilmai.profiles.payload.OnboardingRequest;
import org.aiincubator.ilmai.profiles.payload.OnboardingResponse;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OnboardingServiceTest {

    private final UUID userId = UUID.randomUUID();
    private final CurrentUser currentUser = new CurrentUser(userId);

    private final ProfileRepository profiles = mock(ProfileRepository.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final OnboardingService service =
            new OnboardingService(profiles, new OnboardingMapperImpl(), eventPublisher);

    @Test
    void submitPersistsProvidedFields() {
        Profile profile = profileFor(userId);
        when(profiles.findById(userId)).thenReturn(Optional.of(profile));

        LocalDate target = LocalDate.now().plusDays(60);
        OnboardingResponse response = service.submit(currentUser,
                new OnboardingRequest("Pass IELTS", target, 45, LocalTime.of(19, 0), null));

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

        OnboardingRequest req = new OnboardingRequest(null, LocalDate.now().minusDays(1), null, null, null);
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
    void submitPersistsOnboardingPassedFlag() {
        Profile profile = profileFor(userId);
        when(profiles.findById(userId)).thenReturn(Optional.of(profile));

        OnboardingResponse passed = service.submit(currentUser,
                new OnboardingRequest(null, null, null, null, true));
        assertThat(profile.getOnboardingPassed()).isTrue();
        assertThat(passed.getOnboardingPassed()).isTrue();

        OnboardingResponse skipped = service.submit(currentUser,
                new OnboardingRequest(null, null, null, null, false));
        assertThat(profile.getOnboardingPassed()).isFalse();
        assertThat(skipped.getOnboardingPassed()).isFalse();
    }

    @Test
    void submitLeavesOnboardingPassedUnchangedWhenAbsent() {
        Profile profile = profileFor(userId);
        profile.setOnboardingPassed(true);
        when(profiles.findById(userId)).thenReturn(Optional.of(profile));

        service.submit(currentUser, new OnboardingRequest());

        assertThat(profile.getOnboardingPassed()).isTrue();
    }

    @Test
    void getReturnsNullOnboardingPassedByDefault() {
        Profile profile = profileFor(userId);
        when(profiles.findById(userId)).thenReturn(Optional.of(profile));

        OnboardingResponse response = service.get(currentUser);

        assertThat(response.getOnboardingPassed()).isNull();
    }

    @Test
    void submitPublishesGoalUpdatedEventWhenGoalChanges() {
        Profile profile = profileFor(userId);
        when(profiles.findById(userId)).thenReturn(Optional.of(profile));

        service.submit(currentUser, new OnboardingRequest("Pass IELTS", null, null, null, null));

        verify(eventPublisher).publishEvent(any(GoalUpdatedEvent.class));
    }

    @Test
    void submitDoesNotPublishGoalUpdatedEventWhenGoalUnchanged() {
        Profile profile = profileFor(userId);
        when(profiles.findById(userId)).thenReturn(Optional.of(profile));

        service.submit(currentUser, new OnboardingRequest(null, null, 30, null, null));

        verify(eventPublisher, never()).publishEvent(any(GoalUpdatedEvent.class));
    }

    @Test
    void submitPublishesOnboardingCompletedEventOnFirstCompletion() {
        Profile profile = profileFor(userId);
        when(profiles.findById(userId)).thenReturn(Optional.of(profile));

        service.submit(currentUser, new OnboardingRequest(null, null, null, null, true));

        verify(eventPublisher).publishEvent(any(OnboardingCompletedEvent.class));
    }

    @Test
    void submitDoesNotRepublishOnboardingCompletedWhenAlreadyPassed() {
        Profile profile = profileFor(userId);
        profile.setOnboardingPassed(true);
        when(profiles.findById(userId)).thenReturn(Optional.of(profile));

        service.submit(currentUser, new OnboardingRequest(null, null, null, null, true));

        verify(eventPublisher, never()).publishEvent(any(OnboardingCompletedEvent.class));
    }

    @Test
    void submitDoesNotPublishOnboardingCompletedWhenSkipped() {
        Profile profile = profileFor(userId);
        when(profiles.findById(userId)).thenReturn(Optional.of(profile));

        service.submit(currentUser, new OnboardingRequest(null, null, null, null, false));

        verify(eventPublisher, never()).publishEvent(any(OnboardingCompletedEvent.class));
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
