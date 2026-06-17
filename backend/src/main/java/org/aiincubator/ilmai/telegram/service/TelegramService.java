package org.aiincubator.ilmai.telegram.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aiincubator.ilmai.common.CurrentUser;
import org.aiincubator.ilmai.profiles.ProfilesApi;
import org.aiincubator.ilmai.telegram.config.TelegramProperties;
import org.aiincubator.ilmai.telegram.domain.TelegramLink;
import org.aiincubator.ilmai.telegram.domain.TelegramLinkRepository;
import org.aiincubator.ilmai.telegram.payload.TelegramLinkResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TelegramService {

    private static final int CODE_LENGTH = 8;
    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_TTL_MINUTES = 30;

    private final TelegramLinkRepository links;
    private final ProfilesApi profilesApi;
    private final TelegramApiClient telegramApiClient;
    private final TelegramProperties properties;
    private final TelegramMapper telegramMapper;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public TelegramLinkResponse createLinkCode(CurrentUser currentUser) {
        if (!telegramApiClient.isEnabled()) {
            throw new TelegramException(TelegramException.Reason.TELEGRAM_DISABLED);
        }
        UUID userId = currentUser.getUserId();
        TelegramLink link = links.findByUserId(userId).orElseGet(() -> {
            TelegramLink created = new TelegramLink();
            created.setUserId(userId);
            return created;
        });
        if (link.getLinkedAt() != null) {
            throw new TelegramException(TelegramException.Reason.TELEGRAM_ALREADY_LINKED);
        }
        link.setLinkCode(generateCode());
        link.setLinkCodeExpiresAt(OffsetDateTime.now().plusMinutes(CODE_TTL_MINUTES));
        TelegramLink saved = links.save(link);
        TelegramLinkResponse response = telegramMapper.toResponse(saved);
        response.setBotUsername(properties.getBotUsername());
        return response;
    }

    @Transactional(readOnly = true)
    public TelegramLinkResponse getLink(CurrentUser currentUser) {
        TelegramLink link = links.findByUserId(currentUser.getUserId())
                .orElseThrow(() -> new TelegramException(TelegramException.Reason.TELEGRAM_NOT_LINKED));
        TelegramLinkResponse response = telegramMapper.toResponse(link);
        response.setBotUsername(properties.getBotUsername());
        return response;
    }

    @Transactional
    public void unlink(CurrentUser currentUser) {
        links.findByUserId(currentUser.getUserId()).ifPresent(links::delete);
    }

    @Transactional(readOnly = true)
    public Optional<UUID> findLinkedUser(long chatId) {
        return links.findByChatId(chatId)
                .filter(link -> link.getLinkedAt() != null)
                .map(TelegramLink::getUserId);
    }

    @Transactional
    public Optional<UUID> linkChat(long chatId, Long telegramUserId, String username, String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        TelegramLink link = links.findByLinkCode(code.trim()).orElse(null);
        if (link == null || link.getLinkCodeExpiresAt() == null
                || link.getLinkCodeExpiresAt().isBefore(OffsetDateTime.now())) {
            return Optional.empty();
        }
        OffsetDateTime now = OffsetDateTime.now();
        link.setChatId(chatId);
        link.setTelegramUserId(telegramUserId);
        link.setTelegramUsername(username);
        link.setLinkedAt(now);
        link.setLinkCode(null);
        link.setLinkCodeExpiresAt(null);
        link.setLastSeenAt(now);
        links.save(link);
        profilesApi.touchActivity(link.getUserId());
        return Optional.of(link.getUserId());
    }

    @Transactional
    public boolean unlinkChat(long chatId) {
        TelegramLink link = links.findByChatId(chatId).orElse(null);
        if (link == null) {
            return false;
        }
        UUID userId = link.getUserId();
        links.delete(link);
        log.info("telegram link removed for user {}", userId);
        return true;
    }

    @Transactional
    public void markSeen(long chatId) {
        links.findByChatId(chatId).ifPresent(link -> {
            link.setLastSeenAt(OffsetDateTime.now());
            links.save(link);
            profilesApi.touchActivity(link.getUserId());
        });
    }

    private String generateCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CODE_ALPHABET.charAt(secureRandom.nextInt(CODE_ALPHABET.length())));
        }
        return sb.toString();
    }
}
