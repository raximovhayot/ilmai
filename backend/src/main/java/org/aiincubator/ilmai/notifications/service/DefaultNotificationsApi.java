package org.aiincubator.ilmai.notifications.service;

import lombok.RequiredArgsConstructor;
import org.aiincubator.ilmai.notifications.NotificationsApi;
import org.aiincubator.ilmai.notifications.OutboxMessageDto;
import org.aiincubator.ilmai.notifications.OutboxMessageRequest;
import org.aiincubator.ilmai.notifications.domain.OutboxMessage;
import org.aiincubator.ilmai.notifications.domain.OutboxMessageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DefaultNotificationsApi implements NotificationsApi {

    private final OutboxMessageRepository outbox;
    private final NotificationsApiMapper notificationsApiMapper;
    private final Clock clock;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public OutboxMessageDto enqueue(OutboxMessageRequest request) {
        if (request.getDedupeKey() != null) {
            OutboxMessage existing = outbox.findByDedupeKey(request.getDedupeKey()).orElse(null);
            if (existing != null) {
                return notificationsApiMapper.toDto(existing);
            }
        }
        OutboxMessage message = new OutboxMessage();
        message.setUserId(request.getUserId());
        message.setChannel(request.getChannel());
        message.setType(request.getType());
        message.setBody(request.getBody());
        message.setDedupeKey(request.getDedupeKey());
        message.setScheduledFor(request.getScheduledFor());
        return notificationsApiMapper.toDto(outbox.save(message));
    }

    @Override
    @Transactional(readOnly = true)
    public List<OutboxMessageDto> findPending(OffsetDateTime asOf) {
        return outbox.findBySentAtIsNullAndScheduledForLessThanEqualOrderByScheduledForAsc(asOf)
                .stream()
                .map(notificationsApiMapper::toDto)
                .toList();
    }

    @Override
    @Transactional
    public void markSent(UUID id) {
        outbox.findById(id).ifPresent(message -> {
            if (message.getSentAt() == null) {
                message.setSentAt(OffsetDateTime.now(clock));
            }
        });
    }
}
