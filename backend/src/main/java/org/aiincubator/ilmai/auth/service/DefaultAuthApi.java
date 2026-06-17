package org.aiincubator.ilmai.auth.service;

import lombok.RequiredArgsConstructor;
import org.aiincubator.ilmai.auth.AuthApi;
import org.aiincubator.ilmai.auth.UserDto;
import org.aiincubator.ilmai.auth.domain.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DefaultAuthApi implements AuthApi {

    private final UserRepository users;
    private final AuthApiMapper authApiMapper;

    @Override
    @Transactional(readOnly = true)
    public UserDto requireUser(UUID userId) {
        return users.findById(userId)
                .map(authApiMapper::toUserDto)
                .orElseThrow(() -> new AuthException(AuthException.Reason.USER_NOT_FOUND));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserDto> findUser(UUID userId) {
        return users.findById(userId).map(authApiMapper::toUserDto);
    }
}
