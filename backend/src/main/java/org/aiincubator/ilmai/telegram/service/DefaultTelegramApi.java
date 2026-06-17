package org.aiincubator.ilmai.telegram.service;

import lombok.RequiredArgsConstructor;
import org.aiincubator.ilmai.telegram.TelegramApi;
import org.aiincubator.ilmai.telegram.domain.TelegramLink;
import org.aiincubator.ilmai.telegram.domain.TelegramLinkRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DefaultTelegramApi implements TelegramApi {

    private final TelegramLinkRepository links;
    private final TelegramApiClient telegramApiClient;

    @Override
    public boolean isEnabled() {
        return telegramApiClient.isEnabled();
    }

    @Override
    public boolean sendMessage(UUID userId, String text) {
        if (userId == null || text == null || text.isBlank()) {
            return false;
        }
        Long chatId = links.findByUserId(userId)
                .filter(link -> link.getLinkedAt() != null)
                .map(TelegramLink::getChatId)
                .orElse(null);
        if (chatId == null) {
            return false;
        }
        return telegramApiClient.sendMessage(chatId, text);
    }
}
