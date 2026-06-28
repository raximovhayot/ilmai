package org.aiincubator.ilmai.rooms.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoomRepository extends JpaRepository<Room, UUID> {
    List<Room> findAllByOwnerId(UUID ownerId);

    Optional<Room> findFirstByOwnerIdAndPersonalTrueOrderByCreatedAtAsc(UUID ownerId);
}
