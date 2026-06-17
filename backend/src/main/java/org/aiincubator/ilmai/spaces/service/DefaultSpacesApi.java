package org.aiincubator.ilmai.spaces.service;

import lombok.RequiredArgsConstructor;
import org.aiincubator.ilmai.spaces.SpaceDto;
import org.aiincubator.ilmai.spaces.SpacesApi;
import org.aiincubator.ilmai.spaces.domain.Space;
import org.aiincubator.ilmai.spaces.domain.SpaceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DefaultSpacesApi implements SpacesApi {

    private final SpaceRepository spaces;
    private final SpacesApiMapper spacesApiMapper;

    @Override
    @Transactional(readOnly = true)
    public Optional<SpaceDto> findPrimaryForUser(UUID userId) {
        List<Space> userSpaces = spaces.findAllByUserId(userId);
        if (userSpaces.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(spacesApiMapper.toSpaceDto(userSpaces.getFirst()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> findSpaceIdsForUser(UUID userId) {
        return spaces.findAllByUserId(userId).stream().map(Space::getId).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SpaceDto> findById(UUID spaceId) {
        return spaces.findById(spaceId).map(spacesApiMapper::toSpaceDto);
    }
}
