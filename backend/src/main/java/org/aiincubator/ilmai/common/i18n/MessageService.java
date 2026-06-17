package org.aiincubator.ilmai.common.i18n;

import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
@RequiredArgsConstructor
public class MessageService {

    private final MessageSource messageSource;

    public String get(String key) {
        return get(key, null, currentLocale());
    }

    public String get(String key, Object[] args) {
        return get(key, args, currentLocale());
    }

    public String get(String key, Object[] args, Locale locale) {
        Locale effective = resolve(locale);
        return messageSource.getMessage(key, args, key, effective);
    }

    private Locale currentLocale() {
        return resolve(LocaleContextHolder.getLocale());
    }

    private Locale resolve(Locale requested) {
        if (requested == null) {
            return SupportedLocale.DEFAULT.getLocale();
        }
        return SupportedLocale.fromLanguageTag(requested.toLanguageTag())
                .map(SupportedLocale::getLocale)
                .orElse(SupportedLocale.DEFAULT.getLocale());
    }
}
