package org.aiincubator.ilmai.auth.service;

import org.aiincubator.ilmai.auth.domain.User;
import org.aiincubator.ilmai.auth.payload.MeResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AuthMapper {

    MeResponse toMeResponse(User user);
}
