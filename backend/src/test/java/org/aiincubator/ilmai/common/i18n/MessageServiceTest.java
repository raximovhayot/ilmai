package org.aiincubator.ilmai.common.i18n;

import org.aiincubator.ilmai.common.config.LocalizationConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class MessageServiceTest {

    private final MessageSource messageSource = new LocalizationConfig().messageSource();
    private final MessageService service = new MessageService(messageSource);

    @AfterEach
    void resetLocale() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    void resolves_english_by_default() {
        String message = service.get("auth.error.invalidGoogleToken", null, Locale.ENGLISH);

        assertThat(message).isEqualTo("Google ID token is invalid");
    }

    @Test
    void resolves_russian_when_requested() {
        String message = service.get("auth.error.invalidGoogleToken", null, Locale.forLanguageTag("ru"));

        assertThat(message).isEqualTo("Идентификационный токен Google недействителен");
    }

    @Test
    void resolves_uzbek_when_requested() {
        String message = service.get("auth.error.invalidGoogleToken", null, Locale.forLanguageTag("uz"));

        assertThat(message).isEqualTo("Google identifikatsiya tokeni yaroqsiz");
    }

    @Test
    void falls_back_to_default_for_unsupported_locale() {
        String message = service.get("auth.error.invalidGoogleToken", null, Locale.forLanguageTag("de"));

        assertThat(message).isEqualTo("Google ID token is invalid");
    }

    @Test
    void falls_back_to_default_for_null_locale() {
        String message = service.get("auth.error.invalidGoogleToken", null, null);

        assertThat(message).isEqualTo("Google ID token is invalid");
    }

    @Test
    void honors_locale_context_holder_when_no_explicit_locale() {
        LocaleContextHolder.setLocale(Locale.forLanguageTag("ru"));

        String message = service.get("auth.error.userNotFound");

        assertThat(message).isEqualTo("Пользователь не найден");
    }

    @Test
    void interpolates_message_arguments() {
        String message = service.get("error.typeMismatch", new Object[]{"userId"}, Locale.ENGLISH);

        assertThat(message).isEqualTo("Parameter 'userId' has an invalid value");
    }

    @Test
    void unknown_key_returns_the_key_itself() {
        String message = service.get("some.unknown.key", null, Locale.ENGLISH);

        assertThat(message).isEqualTo("some.unknown.key");
    }

    @Test
    void supportedLocale_maps_language_tag_case_insensitively() {
        assertThat(SupportedLocale.fromLanguageTag("RU")).contains(SupportedLocale.RU);
        assertThat(SupportedLocale.fromLanguageTag("uz-Latn")).contains(SupportedLocale.UZ);
        assertThat(SupportedLocale.fromLanguageTag("en-US")).contains(SupportedLocale.EN);
        assertThat(SupportedLocale.fromLanguageTag(null)).isEmpty();
        assertThat(SupportedLocale.fromLanguageTag("")).isEmpty();
        assertThat(SupportedLocale.fromLanguageTag("xx")).isEmpty();
    }
}
