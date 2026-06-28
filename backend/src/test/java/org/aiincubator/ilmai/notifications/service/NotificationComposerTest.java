package org.aiincubator.ilmai.notifications.service;

import org.aiincubator.ilmai.common.i18n.MessageService;
import org.aiincubator.ilmai.common.i18n.SupportedLocale;
import org.aiincubator.ilmai.profiles.ProfileDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationComposerTest {

    @Mock MessageService messageService;
    @InjectMocks NotificationComposer composer;

    private final UUID userId = UUID.randomUUID();

    @Test
    void dailyReminder_withStreakAndStep_usesStreakStepKeyAndProfileLocale() {
        when(messageService.get(eq("notification.reminder.streak.step"), any(), eq(Locale.forLanguageTag("ru"))))
                .thenReturn("body");

        String body = composer.composeDailyReminder(5, "Chapter 3", 0, profile(SupportedLocale.RU));

        assertThat(body).isEqualTo("body");
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(messageService).get(eq("notification.reminder.streak.step"), args.capture(), any());
        assertThat(args.getValue()).containsExactly(5, "Chapter 3");
    }

    @Test
    void dailyReminder_withStreakNoStep_usesStreakNoStepKey() {
        when(messageService.get(eq("notification.reminder.streak.noStep"), any(), any())).thenReturn("b");

        composer.composeDailyReminder(3, null, 0, profile(SupportedLocale.EN));

        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(messageService).get(eq("notification.reminder.streak.noStep"), args.capture(), any());
        assertThat(args.getValue()).containsExactly(3);
    }

    @Test
    void dailyReminder_noStreakWithStep_usesNoStreakStepKey() {
        when(messageService.get(eq("notification.reminder.noStreak.step"), any(), any())).thenReturn("b");

        composer.composeDailyReminder(0, "Chapter 1", 0, profile(SupportedLocale.EN));

        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(messageService).get(eq("notification.reminder.noStreak.step"), args.capture(), any());
        assertThat(args.getValue()).containsExactly("Chapter 1");
    }

    @Test
    void dailyReminder_noStreakNoStep_usesNoStreakNoStepKeyWithoutArgs() {
        when(messageService.get(eq("notification.reminder.noStreak.noStep"), isNull(), any())).thenReturn("b");

        composer.composeDailyReminder(0, null, 0, profile(SupportedLocale.EN));

        verify(messageService).get(eq("notification.reminder.noStreak.noStep"), isNull(), any());
    }

    @Test
    void dailyReminder_withDueReviews_appendsReviewsDueLine() {
        when(messageService.get(eq("notification.reminder.streak.step"), any(), any())).thenReturn("base");
        when(messageService.get(eq("notification.reminder.reviewsDue"), any(), any())).thenReturn("Due: 3.");

        String body = composer.composeDailyReminder(5, "Chapter 3", 3, profile(SupportedLocale.EN));

        assertThat(body).isEqualTo("base Due: 3.");
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(messageService).get(eq("notification.reminder.reviewsDue"), args.capture(), any());
        assertThat(args.getValue()).containsExactly(3);
    }

    @Test
    void brokenStreak_usesBrokenKeyWithLength() {
        when(messageService.get(eq("notification.streak.broken"), any(), any())).thenReturn("b");

        composer.composeBrokenStreak(4, profile(SupportedLocale.EN));

        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(messageService).get(eq("notification.streak.broken"), args.capture(), any());
        assertThat(args.getValue()).containsExactly(4);
    }

    @Test
    void milestone_usesMilestoneKeyWithLevel() {
        when(messageService.get(eq("notification.streak.milestone"), any(), any())).thenReturn("b");

        composer.composeMilestone(30, profile(SupportedLocale.EN));

        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(messageService).get(eq("notification.streak.milestone"), args.capture(), any());
        assertThat(args.getValue()).containsExactly(30);
    }

    @Test
    void nullProfile_fallsBackToDefaultLocale() {
        when(messageService.get(any(), any(), eq(SupportedLocale.DEFAULT.getLocale()))).thenReturn("b");

        composer.composeMilestone(7, null);

        verify(messageService).get(eq("notification.streak.milestone"), any(),
                eq(SupportedLocale.DEFAULT.getLocale()));
    }

    private ProfileDto profile(SupportedLocale locale) {
        return new ProfileDto(userId, locale, "UTC", null, 0, 0, 0, null);
    }
}
