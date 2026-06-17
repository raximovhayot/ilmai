package org.aiincubator.ilmai.telegram.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TelegramLinkRepository extends JpaRepository<TelegramLink, UUID> {

    Optional<TelegramLink> findByUserId(UUID userId);

    Optional<TelegramLink> findByChatId(Long chatId);

    Optional<TelegramLink> findByLinkCode(String linkCode);
}
