package org.aiincubator.ilmai.rooms;

import org.aiincubator.ilmai.common.CurrentUser;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoomsApi {

    Optional<RoomDto> findPersonalForUser(UUID userId);

    Optional<RoomGoalDto> findPersonalGoalForUser(UUID userId);

    Optional<RoomGoalDto> applyGoalPatch(UUID userId, String goal, LocalDate targetDate, Integer dailyStudyMinutes);

    List<UUID> findRoomIdsForUser(UUID userId);

    Optional<RoomDto> findById(UUID roomId);

    RoomDto requireMember(CurrentUser currentUser, UUID roomId);

    RoomDto requireOwner(CurrentUser currentUser, UUID roomId);

    Optional<RoomGoalDto> findGoal(UUID roomId);
}
