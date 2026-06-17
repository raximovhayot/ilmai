package org.aiincubator.ilmai.streaks.service;

import org.aiincubator.ilmai.streaks.domain.StreakActivityDayRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StreakRolloverJobTest {

    @Mock StreakActivityDayRepository activityDays;
    @Mock StreakService streakService;

    @InjectMocks StreakRolloverJob job;

    @Test
    void run_rollsOverEachDistinctActiveUser() {
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        when(activityDays.findDistinctUserIds()).thenReturn(List.of(userA, userB));

        job.run();

        verify(streakService).rolloverYesterday(userA);
        verify(streakService).rolloverYesterday(userB);
        verifyNoMoreInteractions(streakService);
    }

    @Test
    void run_noActiveUsers_doesNothing() {
        when(activityDays.findDistinctUserIds()).thenReturn(List.of());

        job.run();

        verifyNoMoreInteractions(streakService);
    }
}
