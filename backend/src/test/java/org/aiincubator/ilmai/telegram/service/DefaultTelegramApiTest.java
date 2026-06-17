package org.aiincubator.ilmai.telegram.service;

import org.aiincubator.ilmai.telegram.domain.TelegramLink;
import org.aiincubator.ilmai.telegram.domain.TelegramLinkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultTelegramApiTest {

    @Mock TelegramLinkRepository links;
    @Mock TelegramApiClient telegramApiClient;

    private DefaultTelegramApi api;

    @BeforeEach
    void setUp() {
        api = new DefaultTelegramApi(links, telegramApiClient);
    }

    @Test
    void sendMessage_linkedUser_sendsToTheirChatId() {
        UUID userId = UUID.randomUUID();
        when(links.findByUserId(userId)).thenReturn(Optional.of(linkedTo(123L)));
        when(telegramApiClient.sendMessage(123L, "hello")).thenReturn(true);

        assertThat(api.sendMessage(userId, "hello")).isTrue();

        verify(telegramApiClient).sendMessage(123L, "hello");
    }

    @Test
    void sendMessage_noLink_returnsFalseWithoutSending() {
        UUID userId = UUID.randomUUID();
        when(links.findByUserId(userId)).thenReturn(Optional.empty());

        assertThat(api.sendMessage(userId, "hello")).isFalse();

        verify(telegramApiClient, never()).sendMessage(anyLong(), anyString());
    }

    @Test
    void sendMessage_pendingLinkWithoutChat_returnsFalse() {
        UUID userId = UUID.randomUUID();
        TelegramLink pending = new TelegramLink();
        pending.setUserId(userId);

        when(links.findByUserId(userId)).thenReturn(Optional.of(pending));

        assertThat(api.sendMessage(userId, "hello")).isFalse();

        verify(telegramApiClient, never()).sendMessage(anyLong(), anyString());
    }

    @Test
    void sendMessage_blankText_returnsFalseWithoutLookup() {
        assertThat(api.sendMessage(UUID.randomUUID(), "  ")).isFalse();

        verifyNoInteractions(links, telegramApiClient);
    }

    private TelegramLink linkedTo(long chatId) {
        TelegramLink link = new TelegramLink();
        link.setUserId(UUID.randomUUID());
        link.setChatId(chatId);
        link.setLinkedAt(OffsetDateTime.parse("2026-05-01T00:00:00Z"));
        return link;
    }
}
