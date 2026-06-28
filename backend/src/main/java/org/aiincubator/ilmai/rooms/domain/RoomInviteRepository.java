package org.aiincubator.ilmai.rooms.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RoomInviteRepository extends JpaRepository<RoomInvite, UUID> {

    Optional<RoomInvite> findByRoomIdAndRevokedFalse(UUID roomId);

    Optional<RoomInvite> findByCodeAndRevokedFalse(String code);
}
