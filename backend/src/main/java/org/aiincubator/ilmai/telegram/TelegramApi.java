package org.aiincubator.ilmai.telegram;

import java.util.UUID;

public interface TelegramApi {

    boolean isEnabled();

    boolean sendMessage(UUID userId, String text);
}
