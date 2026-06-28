package org.aiincubator.ilmai.rooms.service;

import lombok.RequiredArgsConstructor;
import org.aiincubator.ilmai.common.CurrentUser;
import org.aiincubator.ilmai.rooms.RoomDto;
import org.aiincubator.ilmai.rooms.RoomGoalDto;
import org.aiincubator.ilmai.rooms.RoomGoalUpdatedEvent;
import org.aiincubator.ilmai.rooms.RoomsApi;
import org.aiincubator.ilmai.rooms.domain.Room;
import org.aiincubator.ilmai.rooms.domain.RoomMember;
import org.aiincubator.ilmai.rooms.domain.RoomMemberRepository;
import org.aiincubator.ilmai.rooms.domain.RoomRepository;
import org.aiincubator.ilmai.rooms.domain.RoomRole;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DefaultRoomsApi implements RoomsApi {

    private final RoomRepository rooms;
    private final RoomMemberRepository roomMembers;
    private final RoomsApiMapper roomsApiMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional(readOnly = true)
    public Optional<RoomDto> findPersonalForUser(UUID userId) {
        return rooms.findFirstByOwnerIdAndPersonalTrueOrderByCreatedAtAsc(userId)
                .map(roomsApiMapper::toRoomDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RoomGoalDto> findPersonalGoalForUser(UUID userId) {
        return rooms.findFirstByOwnerIdAndPersonalTrueOrderByCreatedAtAsc(userId)
                .map(roomsApiMapper::toRoomGoalDto);
    }

    @Override
    @Transactional
    public Optional<RoomGoalDto> applyGoalPatch(UUID userId, String goal, LocalDate targetDate,
                                                Integer dailyStudyMinutes) {
        return rooms.findFirstByOwnerIdAndPersonalTrueOrderByCreatedAtAsc(userId).map(room -> {
            boolean goalChanged = false;
            if (goal != null) {
                String trimmed = goal.trim();
                room.setGoal(trimmed.isEmpty() ? null : trimmed);
                goalChanged = true;
            }
            if (targetDate != null) {
                if (targetDate.isBefore(LocalDate.now())) {
                    throw new RoomException(RoomException.Reason.INVALID_TARGET_DATE);
                }
                room.setTargetDate(targetDate);
                goalChanged = true;
            }
            if (dailyStudyMinutes != null) {
                room.setDailyStudyMinutes(dailyStudyMinutes);
            }
            if (goalChanged) {
                eventPublisher.publishEvent(new RoomGoalUpdatedEvent(userId, room.getId()));
            }
            return roomsApiMapper.toRoomGoalDto(room);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> findRoomIdsForUser(UUID userId) {
        return roomMembers.findRoomIdsByUserId(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RoomDto> findById(UUID roomId) {
        return rooms.findById(roomId).map(roomsApiMapper::toRoomDto);
    }

    @Override
    @Transactional(readOnly = true)
    public RoomDto requireMember(CurrentUser currentUser, UUID roomId) {
        Room room = requireRoom(roomId);
        if (!roomMembers.existsByRoomIdAndUserId(roomId, currentUser.getUserId())) {
            throw new RoomException(RoomException.Reason.NOT_A_MEMBER);
        }
        return roomsApiMapper.toRoomDto(room);
    }

    @Override
    @Transactional(readOnly = true)
    public RoomDto requireOwner(CurrentUser currentUser, UUID roomId) {
        Room room = requireRoom(roomId);
        RoomMember member = roomMembers.findByRoomIdAndUserId(roomId, currentUser.getUserId())
                .orElseThrow(() -> new RoomException(RoomException.Reason.NOT_A_MEMBER));
        if (member.getRole() != RoomRole.OWNER) {
            throw new RoomException(RoomException.Reason.NOT_OWNER);
        }
        return roomsApiMapper.toRoomDto(room);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RoomGoalDto> findGoal(UUID roomId) {
        return rooms.findById(roomId).map(roomsApiMapper::toRoomGoalDto);
    }

    private Room requireRoom(UUID roomId) {
        return rooms.findById(roomId)
                .orElseThrow(() -> new RoomException(RoomException.Reason.ROOM_NOT_FOUND));
    }
}
