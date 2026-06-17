package org.aiincubator.ilmai.notifications.service;

import org.aiincubator.ilmai.notifications.OutboxMessageDto;
import org.aiincubator.ilmai.notifications.domain.OutboxMessage;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface NotificationsApiMapper {

    OutboxMessageDto toDto(OutboxMessage message);
}
