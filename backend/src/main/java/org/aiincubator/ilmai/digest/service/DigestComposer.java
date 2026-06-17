package org.aiincubator.ilmai.digest.service;

import lombok.RequiredArgsConstructor;
import org.aiincubator.ilmai.common.i18n.MessageService;
import org.aiincubator.ilmai.common.i18n.SupportedLocale;
import org.aiincubator.ilmai.digest.domain.WeeklyDigest;
import org.aiincubator.ilmai.profiles.ProfileDto;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
@RequiredArgsConstructor
public class DigestComposer {

    private final MessageService messageService;

    public String compose(WeeklyDigest digest, ProfileDto profile) {
        Locale locale = localeOf(profile);
        return switch (digest.getVariant()) {
            case NEW_USER -> messageService.get("notification.digest.new", null, locale);
            case INACTIVE -> messageService.get("notification.digest.inactive", null, locale);
            case FULL -> composeFull(digest, locale);
        };
    }

    private String composeFull(WeeklyDigest digest, Locale locale) {
        String score = digest.getAvgScore() == null ? "—" : digest.getAvgScore() + "%";
        String header = messageService.get("notification.digest.full",
                new Object[]{digest.getQuizzes(), digest.getAnswered(), score, digest.getStreakNow()}, locale);
        String stand = digest.getWhereYouStand();
        if (stand != null && !stand.isBlank()) {
            return header + "\n\n" + stand.trim();
        }
        return header;
    }

    private static Locale localeOf(ProfileDto profile) {
        SupportedLocale locale = profile != null && profile.getLocale() != null
                ? profile.getLocale()
                : SupportedLocale.DEFAULT;
        return locale.getLocale();
    }
}
