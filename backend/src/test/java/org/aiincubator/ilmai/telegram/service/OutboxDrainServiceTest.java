package org.aiincubator.ilmai.telegram.service;

import org.aiincubator.ilmai.notifications.NotificationsApi;
import org.aiincubator.ilmai.notifications.OutboxChannel;
import org.aiincubator.ilmai.notifications.OutboxMessageDto;
import org.aiincubator.ilmai.notifications.OutboxMessageType;
import org.aiincubator.ilmai.telegram.TelegramApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxDrainServiceTest {

    @Mock NotificationsApi notificationsApi;
    @Mock TelegramApi telegramApi;

    private final Clock clock = Clock.fixed(Instant.parse("2026-06-01T09:00:00Z"), ZoneOffset.UTC);

    private OutboxDrainService service;

    @BeforeEach
    void setUp() {
        service = new OutboxDrainService(notificationsApi, telegramApi, clock);
    }

    @Test
    void drain_whenTelegramDisabled_doesNothing() {
        when(telegramApi.isEnabled()).thenReturn(false);

        assertThat(service.drain()).isZero();

        verify(notificationsApi, never()).findPending(any());
        verify(telegramApi, never()).sendMessage(any(), any());
        verify(notificationsApi, never()).markSent(any());
    }

    @Test
    void drain_marksOnlyDeliveredMessagesAsSent() {
        UUID linkedUser = UUID.randomUUID();
        UUID unlinkedUser = UUID.randomUUID();
        OutboxMessageDto delivered = message(linkedUser, "time to practice");
        OutboxMessageDto undelivered = message(unlinkedUser, "time to practice");

        when(telegramApi.isEnabled()).thenReturn(true);
        when(notificationsApi.findPending(any())).thenReturn(List.of(delivered, undelivered));
        when(telegramApi.sendMessage(eq(linkedUser), any())).thenReturn(true);
        when(telegramApi.sendMessage(eq(unlinkedUser), any())).thenReturn(false);

        assertThat(service.drain()).isEqualTo(1);

        verify(notificationsApi).markSent(delivered.getId());
        verify(notificationsApi, never()).markSent(undelivered.getId());
    }

    private OutboxMessageDto message(UUID userId, String body) {
        OffsetDateTime now = OffsetDateTime.parse("2026-06-01T09:00:00Z");
        return new OutboxMessageDto(UUID.randomUUID(), userId, OutboxChannel.TELEGRAM,
                OutboxMessageType.DAILY_REMINDER, body, now, null, "k:" + userId, now);
    }
}
