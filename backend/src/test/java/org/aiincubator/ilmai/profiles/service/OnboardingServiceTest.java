package org.aiincubator.ilmai.profiles.service;

import org.aiincubator.ilmai.common.CurrentUser;
import org.aiincubator.ilmai.profiles.OnboardingCompletedEvent;
import org.aiincubator.ilmai.profiles.domain.Profile;
import org.aiincubator.ilmai.profiles.domain.ProfileRepository;
import org.aiincubator.ilmai.profiles.payload.OnboardingRequest;
import org.aiincubator.ilmai.profiles.payload.OnboardingResponse;
import org.aiincubator.ilmai.rooms.RoomGoalDto;
import org.aiincubator.ilmai.rooms.RoomsApi;
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
    private final RoomsApi roomsApi = mock(RoomsApi.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final OnboardingService service =
            new OnboardingService(profiles, new OnboardingMapperImpl(), roomsApi, eventPublisher);

    private RoomGoalDto roomGoal(String goal, LocalDate targetDate, Integer dailyStudyMinutes) {
        return new RoomGoalDto(UUID.randomUUID(), goal, targetDate, dailyStudyMinutes);
    }

    @Test
    void submitWritesGoalToRoomAndReturnsIt() {
        Profile profile = profileFor(userId);
        when(profiles.findById(userId)).thenReturn(Optional.of(profile));
        LocalDate target = LocalDate.now().plusDays(60);
        when(roomsApi.applyGoalPatch(userId, "Pass IELTS", target, 45))
                .thenReturn(Optional.of(roomGoal("Pass IELTS", target, 45)));

        OnboardingResponse response = service.submit(currentUser,
                new OnboardingRequest("Pass IELTS", target, 45, LocalTime.of(19, 0), null));

        verify(roomsApi).applyGoalPatch(userId, "Pass IELTS", target, 45);
        assertThat(profile.getDailyReminder()).isEqualTo(LocalTime.of(19, 0));
        assertThat(response.getGoal()).isEqualTo("Pass IELTS");
        assertThat(response.getTargetDate()).isEqualTo(target);
        assertThat(response.getDailyStudyMinutes()).isEqualTo(45);
        assertThat(response.isTelegramLinked()).isFalse();
    }

    @Test
    void submitWithEmptyRequestReturnsExistingRoomGoal() {
        Profile profile = profileFor(userId);
        when(profiles.findById(userId)).thenReturn(Optional.of(profile));
        when(roomsApi.applyGoalPatch(userId, null, null, null))
                .thenReturn(Optional.of(roomGoal("Existing goal", null, 30)));

        OnboardingResponse response = service.submit(currentUser, new OnboardingRequest());

        assertThat(response.getGoal()).isEqualTo("Existing goal");
        assertThat(response.getDailyStudyMinutes()).isEqualTo(30);
    }

    @Test
    void submitRejectsTargetDateInThePast() {
        Profile profile = profileFor(userId);
        when(profiles.findById(userId)).thenReturn(Optional.of(profile));

        OnboardingRequest req = new OnboardingRequest(null, LocalDate.now().minusDays(1), null, null, null);
        assertThatThrownBy(() -> service.submit(currentUser, req))
                .isInstanceOf(ProfileException.class);
        verify(roomsApi, never()).applyGoalPatch(any(), any(), any(), any());
    }

    @Test
    void getReturnsRoomGoalValuesWithTelegramPlaceholder() {
        Profile profile = profileFor(userId);
        when(profiles.findById(userId)).thenReturn(Optional.of(profile));
        when(roomsApi.findPersonalGoalForUser(userId))
                .thenReturn(Optional.of(roomGoal("Learn SQL", null, 20)));

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
