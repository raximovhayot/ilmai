package org.aiincubator.ilmai.auth;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor
public class UserDto {

    private final UUID id;
    private final String username;
    private final UserStatus status;
}
