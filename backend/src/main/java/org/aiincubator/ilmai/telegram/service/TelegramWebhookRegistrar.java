package org.aiincubator.ilmai.telegram.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TelegramWebhookRegistrar {

    private final TelegramApiClient telegramApiClient;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        telegramApiClient.registerWebhook();
    }
}
