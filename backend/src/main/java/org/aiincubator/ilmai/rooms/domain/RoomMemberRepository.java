package org.aiincubator.ilmai.rooms.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoomMemberRepository extends JpaRepository<RoomMember, UUID> {

    Optional<RoomMember> findByRoomIdAndUserId(UUID roomId, UUID userId);

    List<RoomMember> findAllByRoomIdOrderByCreatedAtAsc(UUID roomId);

    boolean existsByRoomIdAndUserId(UUID roomId, UUID userId);

    @Query("select m.roomId from RoomMember m where m.userId = :userId")
    List<UUID> findRoomIdsByUserId(UUID userId);
}
