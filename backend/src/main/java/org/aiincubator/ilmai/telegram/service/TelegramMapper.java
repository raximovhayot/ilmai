package org.aiincubator.ilmai.telegram.service;

import org.aiincubator.ilmai.telegram.domain.TelegramLink;
import org.aiincubator.ilmai.telegram.payload.TelegramLinkResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface TelegramMapper {

    TelegramLinkResponse toResponse(TelegramLink link);
}
