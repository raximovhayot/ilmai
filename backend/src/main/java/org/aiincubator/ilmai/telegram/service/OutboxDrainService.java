package org.aiincubator.ilmai.telegram.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aiincubator.ilmai.notifications.NotificationsApi;
import org.aiincubator.ilmai.notifications.OutboxChannel;
import org.aiincubator.ilmai.notifications.OutboxMessageDto;
import org.aiincubator.ilmai.telegram.TelegramApi;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxDrainService {

    private final NotificationsApi notificationsApi;
    private final TelegramApi telegramApi;
    private final Clock clock;

    public int drain() {
        if (!telegramApi.isEnabled()) {
            return 0;
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        List<OutboxMessageDto> pending = notificationsApi.findPending(now);
        int delivered = 0;
        for (OutboxMessageDto message : pending) {
            if (message.getChannel() != OutboxChannel.TELEGRAM) {
                continue;
            }
            if (telegramApi.sendMessage(message.getUserId(), message.getBody())) {
                notificationsApi.markSent(message.getId());
                delivered++;
            }
        }
        return delivered;
    }
}
