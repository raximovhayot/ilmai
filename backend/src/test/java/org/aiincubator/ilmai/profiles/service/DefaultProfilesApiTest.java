package org.aiincubator.ilmai.profiles.service;

import org.aiincubator.ilmai.profiles.GoalUpdatedEvent;
import org.aiincubator.ilmai.profiles.domain.Profile;
import org.aiincubator.ilmai.profiles.domain.ProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultProfilesApiTest {

    @Mock ProfileRepository profiles;
    @Mock ProfilesApiMapper profilesApiMapper;
    @Mock ApplicationEventPublisher eventPublisher;

    @InjectMocks DefaultProfilesApi api;

    @Test
    void updateGoalPublishesGoalUpdatedEventForTheUser() {
        UUID user = UUID.randomUUID();
        when(profiles.findById(user)).thenReturn(Optional.of(mock(Profile.class)));

        api.updateGoal(user, "IELTS", null);

        ArgumentCaptor<GoalUpdatedEvent> captor = ArgumentCaptor.forClass(GoalUpdatedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(user);
    }

    @Test
    void updateGoalDoesNotPublishWhenNothingChanges() {
        UUID user = UUID.randomUUID();
        when(profiles.findById(user)).thenReturn(Optional.of(mock(Profile.class)));

        api.updateGoal(user, null, null);

        verify(eventPublisher, never()).publishEvent(any(GoalUpdatedEvent.class));
    }
}
