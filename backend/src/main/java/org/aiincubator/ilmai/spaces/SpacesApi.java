package org.aiincubator.ilmai.spaces;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpacesApi {

    Optional<SpaceDto> findPrimaryForUser(UUID userId);

    List<UUID> findSpaceIdsForUser(UUID userId);

    Optional<SpaceDto> findById(UUID spaceId);
}
