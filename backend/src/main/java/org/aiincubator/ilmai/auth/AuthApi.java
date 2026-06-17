package org.aiincubator.ilmai.auth;

import java.util.Optional;
import java.util.UUID;

public interface AuthApi {

    UserDto requireUser(UUID userId);

    Optional<UserDto> findUser(UUID userId);
}
