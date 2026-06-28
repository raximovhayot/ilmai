package org.aiincubator.ilmai.materials.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TopicRepository extends JpaRepository<Topic, UUID> {

    List<Topic> findAllByRoomIdInOrderByCreatedAtAsc(Collection<UUID> spaceIds);

    Optional<Topic> findByIdAndRoomIdIn(UUID id, Collection<UUID> spaceIds);

    boolean existsByRoomIdAndNameIgnoreCase(UUID spaceId, String name);

    boolean existsByRoomIdAndNameIgnoreCaseAndIdNot(UUID spaceId, String name, UUID id);
}
