package org.aiincubator.ilmai.common.i18n;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Getter
@AllArgsConstructor
public enum SupportedLocale {

    EN(Locale.forLanguageTag("en")),
    RU(Locale.forLanguageTag("ru")),
    UZ(Locale.forLanguageTag("uz"));

    public static final SupportedLocale DEFAULT = EN;

    private final Locale locale;

    public static List<Locale> all() {
        return Arrays.stream(values()).map(SupportedLocale::getLocale).toList();
    }

    public static Optional<SupportedLocale> fromLanguageTag(String tag) {
        if (tag == null || tag.isBlank()) {
            return Optional.empty();
        }
        String lang = Locale.forLanguageTag(tag).getLanguage();
        if (lang.isBlank()) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(s -> s.locale.getLanguage().equalsIgnoreCase(lang))
                .findFirst();
    }
}
