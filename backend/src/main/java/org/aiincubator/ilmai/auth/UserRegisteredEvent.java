package org.aiincubator.ilmai.auth;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.aiincubator.ilmai.common.i18n.SupportedLocale;

import java.util.UUID;

@Getter
@AllArgsConstructor
public class UserRegisteredEvent {

    private final UUID userId;
    private final SupportedLocale locale;
    private final String firstNameHint;
}
