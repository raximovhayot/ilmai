package org.aiincubator.ilmai.notifications.service;

import lombok.RequiredArgsConstructor;
import org.aiincubator.ilmai.common.i18n.MessageService;
import org.aiincubator.ilmai.common.i18n.SupportedLocale;
import org.aiincubator.ilmai.profiles.ProfileDto;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
@RequiredArgsConstructor
public class NotificationComposer {

    private final MessageService messageService;

    public String composeDailyReminder(int streakCurrent, String stepTitle, int dueReviewCount, ProfileDto profile) {
        Locale locale = localeOf(profile);
        String base = baseReminder(streakCurrent, stepTitle, locale);
        if (dueReviewCount > 0) {
            return base + " " + messageService.get("notification.reminder.reviewsDue",
                    new Object[]{dueReviewCount}, locale);
        }
        return base;
    }

    private String baseReminder(int streakCurrent, String stepTitle, Locale locale) {
        if (streakCurrent > 0) {
            return stepTitle != null
                    ? messageService.get("notification.reminder.streak.step",
                    new Object[]{streakCurrent, stepTitle}, locale)
                    : messageService.get("notification.reminder.streak.noStep",
                    new Object[]{streakCurrent}, locale);
        }
        return stepTitle != null
                ? messageService.get("notification.reminder.noStreak.step",
                new Object[]{stepTitle}, locale)
                : messageService.get("notification.reminder.noStreak.noStep", null, locale);
    }

    public String composeBrokenStreak(int brokenStreakLength, ProfileDto profile) {
        return messageService.get("notification.streak.broken",
                new Object[]{brokenStreakLength}, localeOf(profile));
    }

    public String composeMilestone(int milestone, ProfileDto profile) {
        return messageService.get("notification.streak.milestone",
                new Object[]{milestone}, localeOf(profile));
    }

    private static Locale localeOf(ProfileDto profile) {
        SupportedLocale locale = profile != null && profile.getLocale() != null
                ? profile.getLocale()
                : SupportedLocale.DEFAULT;
        return locale.getLocale();
    }
}
