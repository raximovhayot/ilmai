package org.aiincubator.ilmai.materials.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TopicRepository extends JpaRepository<Topic, UUID> {

    List<Topic> findAllBySpaceIdInOrderByCreatedAtAsc(Collection<UUID> spaceIds);

    Optional<Topic> findByIdAndSpaceIdIn(UUID id, Collection<UUID> spaceIds);

    boolean existsBySpaceIdAndNameIgnoreCase(UUID spaceId, String name);

    boolean existsBySpaceIdAndNameIgnoreCaseAndIdNot(UUID spaceId, String name, UUID id);
}
