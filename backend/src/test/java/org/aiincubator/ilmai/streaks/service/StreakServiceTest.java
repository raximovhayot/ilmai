package org.aiincubator.ilmai.streaks.service;

import org.aiincubator.ilmai.common.i18n.SupportedLocale;
import org.aiincubator.ilmai.profiles.ProfileDto;
import org.aiincubator.ilmai.profiles.ProfilesApi;
import org.aiincubator.ilmai.streaks.StreakBrokenEvent;
import org.aiincubator.ilmai.streaks.StreakMilestoneReachedEvent;
import org.aiincubator.ilmai.streaks.domain.Streak;
import org.aiincubator.ilmai.streaks.domain.StreakActivityDay;
import org.aiincubator.ilmai.streaks.domain.StreakActivityDayRepository;
import org.aiincubator.ilmai.streaks.domain.StreakRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StreakServiceTest {

    @Mock StreakRepository streaks;
    @Mock StreakActivityDayRepository activityDays;
    @Mock ProfilesApi profilesApi;
    @Mock Clock clock;
    @Mock ApplicationEventPublisher eventPublisher;

    @InjectMocks StreakService service;

    private final UUID userId = UUID.randomUUID();

    @Test
    void recordActivity_newDay_savesActivityDayInUserTimezone() {
        when(profilesApi.find(userId)).thenReturn(Optional.of(profile("America/New_York")));
        when(activityDays.existsByUserIdAndActivityDate(userId, LocalDate.parse("2026-06-01")))
                .thenReturn(false);

        service.recordActivity(userId, OffsetDateTime.parse("2026-06-02T02:00:00Z"));

        StreakActivityDay saved = captureActivityDay();
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getActivityDate()).isEqualTo(LocalDate.parse("2026-06-01"));
    }

    @Test
    void recordActivity_noProfile_usesDefaultTashkentZone() {
        when(profilesApi.find(userId)).thenReturn(Optional.empty());
        when(activityDays.existsByUserIdAndActivityDate(userId, LocalDate.parse("2026-06-02")))
                .thenReturn(false);

        service.recordActivity(userId, OffsetDateTime.parse("2026-06-01T20:00:00Z"));

        StreakActivityDay saved = captureActivityDay();
        assertThat(saved.getActivityDate()).isEqualTo(LocalDate.parse("2026-06-02"));
    }

    @Test
    void recordActivity_existingDay_doesNotSave() {
        when(profilesApi.find(userId)).thenReturn(Optional.of(profile("Asia/Tashkent")));
        when(activityDays.existsByUserIdAndActivityDate(any(), any())).thenReturn(true);

        service.recordActivity(userId, OffsetDateTime.parse("2026-06-01T12:00:00Z"));

        verify(activityDays, never()).save(any());
    }

    @Test
    void rollover_firstActiveDay_setsCurrentToOne() {
        when(streaks.findById(userId)).thenReturn(Optional.empty());
        when(activityDays.existsByUserIdAndActivityDate(userId, LocalDate.parse("2026-06-01")))
                .thenReturn(true);

        service.rollover(userId, LocalDate.parse("2026-06-01"));

        Streak saved = captureStreak();
        assertThat(saved.getStreakCurrent()).isEqualTo(1);
        assertThat(saved.getStreakLongest()).isEqualTo(1);
        assertThat(saved.getStreakLastDay()).isEqualTo(LocalDate.parse("2026-06-01"));
    }

    @Test
    void rollover_consecutiveDay_incrementsCurrentAndLongest() {
        when(streaks.findById(userId)).thenReturn(Optional.of(streak(1, 1, "2026-06-01", null)));
        when(activityDays.existsByUserIdAndActivityDate(userId, LocalDate.parse("2026-06-02")))
                .thenReturn(true);

        service.rollover(userId, LocalDate.parse("2026-06-02"));

        Streak saved = captureStreak();
        assertThat(saved.getStreakCurrent()).isEqualTo(2);
        assertThat(saved.getStreakLongest()).isEqualTo(2);
        assertThat(saved.getStreakLastDay()).isEqualTo(LocalDate.parse("2026-06-02"));
    }

    @Test
    void rollover_gapThenActive_resetsToOneAndKeepsLongest() {
        when(streaks.findById(userId)).thenReturn(Optional.of(streak(5, 5, "2026-06-01", null)));
        when(activityDays.existsByUserIdAndActivityDate(userId, LocalDate.parse("2026-06-03")))
                .thenReturn(true);

        service.rollover(userId, LocalDate.parse("2026-06-03"));

        Streak saved = captureStreak();
        assertThat(saved.getStreakCurrent()).isEqualTo(1);
        assertThat(saved.getStreakLongest()).isEqualTo(5);
        assertThat(saved.getStreakLastDay()).isEqualTo(LocalDate.parse("2026-06-03"));
    }

    @Test
    void rollover_missedDayAfterActiveStreak_breaksToZero() {
        when(streaks.findById(userId)).thenReturn(Optional.of(streak(3, 3, "2026-06-01", null)));
        when(activityDays.existsByUserIdAndActivityDate(userId, LocalDate.parse("2026-06-02")))
                .thenReturn(false);

        service.rollover(userId, LocalDate.parse("2026-06-02"));

        Streak saved = captureStreak();
        assertThat(saved.getStreakCurrent()).isZero();
        assertThat(saved.getStreakBrokenAt()).isEqualTo(LocalDate.parse("2026-06-02"));
        assertThat(saved.getStreakLongest()).isEqualTo(3);
        assertThat(saved.getStreakLastDay()).isEqualTo(LocalDate.parse("2026-06-01"));
    }

    @Test
    void rollover_break_publishesBrokenEvent() {
        when(streaks.findById(userId)).thenReturn(Optional.of(streak(3, 3, "2026-06-01", null)));
        when(activityDays.existsByUserIdAndActivityDate(userId, LocalDate.parse("2026-06-02")))
                .thenReturn(false);

        service.rollover(userId, LocalDate.parse("2026-06-02"));

        ArgumentCaptor<StreakBrokenEvent> captor = ArgumentCaptor.forClass(StreakBrokenEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(userId);
        assertThat(captor.getValue().getBrokenDate()).isEqualTo(LocalDate.parse("2026-06-02"));
        assertThat(captor.getValue().getBrokenStreakLength()).isEqualTo(3);
    }

    @Test
    void rollover_reachesMilestone_publishesMilestoneEvent() {
        when(streaks.findById(userId)).thenReturn(Optional.of(streak(6, 6, "2026-06-06", null)));
        when(activityDays.existsByUserIdAndActivityDate(userId, LocalDate.parse("2026-06-07")))
                .thenReturn(true);

        service.rollover(userId, LocalDate.parse("2026-06-07"));

        ArgumentCaptor<StreakMilestoneReachedEvent> captor =
                ArgumentCaptor.forClass(StreakMilestoneReachedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(userId);
        assertThat(captor.getValue().getMilestone()).isEqualTo(7);
    }

    @Test
    void rollover_nonMilestoneDay_publishesNoMilestoneEvent() {
        when(streaks.findById(userId)).thenReturn(Optional.of(streak(1, 1, "2026-06-01", null)));
        when(activityDays.existsByUserIdAndActivityDate(userId, LocalDate.parse("2026-06-02")))
                .thenReturn(true);

        service.rollover(userId, LocalDate.parse("2026-06-02"));

        verify(eventPublisher, never()).publishEvent(any(StreakMilestoneReachedEvent.class));
    }

    @Test
    void rollover_missedDayWithNoActiveStreak_isNoOp() {
        when(streaks.findById(userId)).thenReturn(Optional.of(streak(0, 4, "2026-05-20", null)));
        when(activityDays.existsByUserIdAndActivityDate(userId, LocalDate.parse("2026-06-02")))
                .thenReturn(false);

        service.rollover(userId, LocalDate.parse("2026-06-02"));

        verify(streaks, never()).save(any());
    }

    @Test
    void rollover_alreadyProcessedDay_isIdempotent() {
        when(streaks.findById(userId)).thenReturn(Optional.of(streak(2, 2, "2026-06-02", null)));
        when(activityDays.existsByUserIdAndActivityDate(userId, LocalDate.parse("2026-06-02")))
                .thenReturn(true);

        service.rollover(userId, LocalDate.parse("2026-06-02"));

        verify(streaks, never()).save(any());
    }

    @Test
    void rolloverYesterday_usesClockAndUserTimezoneToRollPreviousDay() {
        when(clock.instant()).thenReturn(Instant.parse("2026-06-02T06:00:00Z"));
        when(profilesApi.find(userId)).thenReturn(Optional.of(profile("UTC")));
        when(streaks.findById(userId)).thenReturn(Optional.empty());
        when(activityDays.existsByUserIdAndActivityDate(userId, LocalDate.parse("2026-06-01")))
                .thenReturn(true);

        service.rolloverYesterday(userId);

        Streak saved = captureStreak();
        assertThat(saved.getStreakCurrent()).isEqualTo(1);
        assertThat(saved.getStreakLastDay()).isEqualTo(LocalDate.parse("2026-06-01"));
    }

    @Test
    void rolloverYesterday_noProfile_usesDefaultTashkentZone() {
        when(clock.instant()).thenReturn(Instant.parse("2026-06-02T19:00:00Z"));
        when(profilesApi.find(userId)).thenReturn(Optional.empty());
        when(streaks.findById(userId)).thenReturn(Optional.empty());
        when(activityDays.existsByUserIdAndActivityDate(userId, LocalDate.parse("2026-06-02")))
                .thenReturn(true);

        service.rolloverYesterday(userId);

        Streak saved = captureStreak();
        assertThat(saved.getStreakLastDay()).isEqualTo(LocalDate.parse("2026-06-02"));
    }

    private ProfileDto profile(String timezone) {
        return new ProfileDto(userId, SupportedLocale.EN, timezone, null, 0, 0, 0, null);
    }

    private Streak streak(int current, int longest, String lastDay, String brokenAt) {
        Streak streak = new Streak();
        streak.setUserId(userId);
        streak.setStreakCurrent(current);
        streak.setStreakLongest(longest);
        streak.setStreakLastDay(lastDay == null ? null : LocalDate.parse(lastDay));
        streak.setStreakBrokenAt(brokenAt == null ? null : LocalDate.parse(brokenAt));
        return streak;
    }

    private StreakActivityDay captureActivityDay() {
        ArgumentCaptor<StreakActivityDay> captor = ArgumentCaptor.forClass(StreakActivityDay.class);
        verify(activityDays).save(captor.capture());
        return captor.getValue();
    }

    private Streak captureStreak() {
        ArgumentCaptor<Streak> captor = ArgumentCaptor.forClass(Streak.class);
        verify(streaks).save(captor.capture());
        return captor.getValue();
    }
}
