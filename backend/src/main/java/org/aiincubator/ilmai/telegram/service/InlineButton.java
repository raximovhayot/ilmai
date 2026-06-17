package org.aiincubator.ilmai.telegram.service;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public final class InlineButton {

    private final String text;
    private final String callbackData;
}
