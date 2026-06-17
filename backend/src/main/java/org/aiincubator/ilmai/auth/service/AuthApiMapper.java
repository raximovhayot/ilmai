package org.aiincubator.ilmai.auth.service;

import org.aiincubator.ilmai.auth.UserDto;
import org.aiincubator.ilmai.auth.domain.User;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AuthApiMapper {

    UserDto toUserDto(User user);
}
