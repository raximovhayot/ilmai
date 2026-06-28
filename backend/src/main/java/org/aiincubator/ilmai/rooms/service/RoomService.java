package org.aiincubator.ilmai.rooms.service;

import lombok.RequiredArgsConstructor;
import org.aiincubator.ilmai.common.CurrentUser;
import org.aiincubator.ilmai.common.i18n.MessageService;
import org.aiincubator.ilmai.common.quota.PremiumFeature;
import org.aiincubator.ilmai.common.quota.QuotaService;
import org.aiincubator.ilmai.rooms.RoomGoalUpdatedEvent;
import org.aiincubator.ilmai.rooms.domain.Room;
import org.aiincubator.ilmai.rooms.domain.RoomMember;
import org.aiincubator.ilmai.rooms.domain.RoomMemberRepository;
import org.aiincubator.ilmai.rooms.domain.RoomRepository;
import org.aiincubator.ilmai.rooms.domain.RoomRole;
import org.aiincubator.ilmai.rooms.payload.RoomResponse;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RoomService {

    private static final int NAME_MAX_LENGTH = 120;
    private static final String KEY_DEFAULT_NAME_WITH_OWNER = "room.defaultName.withOwner";
    private static final String KEY_DEFAULT_NAME_GENERIC = "room.defaultName.generic";

    private final RoomRepository rooms;
    private final RoomMemberRepository roomMembers;
    private final MessageService messages;
    private final RoomMapper roomMapper;
    private final QuotaService quotaService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public Room create(UUID userId, String firstNameHint) {
        String defaultName = computeDefaultName(firstNameHint);
        Room room = new Room();
        room.setOwnerId(userId);
        room.setName(defaultName);
        room.setPersonal(true);
        Room saved = rooms.save(room);
        RoomMember owner = new RoomMember();
        owner.setRoomId(saved.getId());
        owner.setUserId(userId);
        owner.setRole(RoomRole.OWNER);
        roomMembers.save(owner);
        return saved;
    }

    @Transactional
    public RoomResponse createExtra(CurrentUser currentUser, String name) {
        String trimmed = name == null ? null : name.trim();
        if (trimmed == null || trimmed.isEmpty() || trimmed.length() > NAME_MAX_LENGTH) {
            throw new RoomException(RoomException.Reason.NAME_BLANK);
        }
        if (!quotaService.isPremiumFeatureAllowed(currentUser.getUserId(), PremiumFeature.EXTRA_ROOM)) {
            throw new RoomException(RoomException.Reason.PREMIUM_REQUIRED);
        }
        Room room = new Room();
        room.setOwnerId(currentUser.getUserId());
        room.setName(trimmed);
        room.setPersonal(false);
        Room saved = rooms.save(room);
        RoomMember owner = new RoomMember();
        owner.setRoomId(saved.getId());
        owner.setUserId(currentUser.getUserId());
        owner.setRole(RoomRole.OWNER);
        roomMembers.save(owner);
        return roomMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<RoomResponse> getAll(CurrentUser currentUser) {
        return rooms.findAllByOwnerId(currentUser.getUserId()).stream()
                .map(roomMapper::toResponse)
                .toList();
    }

    @Transactional
    public RoomResponse rename(CurrentUser currentUser, UUID roomId, String newName) {
        String trimmed = newName == null ? null : newName.trim();
        if (trimmed == null || trimmed.isEmpty() || trimmed.length() > NAME_MAX_LENGTH) {
            throw new RoomException(RoomException.Reason.NAME_BLANK);
        }
        Room room = rooms.findById(roomId)
                .orElseThrow(() -> new RoomException(RoomException.Reason.ROOM_NOT_FOUND));
        if (!currentUser.getUserId().equals(room.getOwnerId())) {
            throw new RoomException(RoomException.Reason.ROOM_NOT_FOUND);
        }
        room.setName(trimmed);
        return roomMapper.toResponse(room);
    }

    @Transactional
    public RoomResponse updateGoal(CurrentUser currentUser, UUID roomId, String goal,
                                  LocalDate targetDate, Integer dailyStudyMinutes) {
        Room room = rooms.findById(roomId)
                .orElseThrow(() -> new RoomException(RoomException.Reason.ROOM_NOT_FOUND));
        RoomMember member = roomMembers.findByRoomIdAndUserId(roomId, currentUser.getUserId())
                .orElseThrow(() -> new RoomException(RoomException.Reason.NOT_A_MEMBER));
        if (member.getRole() != RoomRole.OWNER) {
            throw new RoomException(RoomException.Reason.NOT_OWNER);
        }
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
            eventPublisher.publishEvent(new RoomGoalUpdatedEvent(room.getOwnerId(), room.getId()));
        }
        return roomMapper.toResponse(room);
    }

    private String computeDefaultName(String firstNameHint) {
        String firstName = extractFirstName(firstNameHint);
        String resolved;
        if (firstName == null) {
            resolved = messages.get(KEY_DEFAULT_NAME_GENERIC);
        } else {
            resolved = messages.get(KEY_DEFAULT_NAME_WITH_OWNER, new Object[]{firstName});
        }
        if (resolved.length() > NAME_MAX_LENGTH) {
            return resolved.substring(0, NAME_MAX_LENGTH);
        }
        return resolved;
    }

    private String extractFirstName(String hint) {
        if (hint == null) {
            return null;
        }
        String trimmed = hint.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        int firstWhitespace = indexOfWhitespace(trimmed);
        return firstWhitespace < 0 ? trimmed : trimmed.substring(0, firstWhitespace);
    }

    private int indexOfWhitespace(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isWhitespace(value.charAt(i))) {
                return i;
            }
        }
        return -1;
    }
}
