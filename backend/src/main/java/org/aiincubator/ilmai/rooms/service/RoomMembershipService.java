package org.aiincubator.ilmai.rooms.service;

import lombok.RequiredArgsConstructor;
import org.aiincubator.ilmai.auth.AuthApi;
import org.aiincubator.ilmai.auth.UserDto;
import org.aiincubator.ilmai.common.CurrentUser;
import org.aiincubator.ilmai.rooms.domain.Room;
import org.aiincubator.ilmai.rooms.domain.RoomInvite;
import org.aiincubator.ilmai.rooms.domain.RoomInviteRepository;
import org.aiincubator.ilmai.rooms.domain.RoomMember;
import org.aiincubator.ilmai.rooms.domain.RoomMemberRepository;
import org.aiincubator.ilmai.rooms.domain.RoomRepository;
import org.aiincubator.ilmai.rooms.domain.RoomRole;
import org.aiincubator.ilmai.rooms.payload.RoomInviteResponse;
import org.aiincubator.ilmai.rooms.payload.RoomMemberResponse;
import org.aiincubator.ilmai.rooms.payload.RoomResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RoomMembershipService {

    private static final int INVITE_CODE_BYTES = 16;

    private final RoomRepository rooms;
    private final RoomMemberRepository roomMembers;
    private final RoomInviteRepository roomInvites;
    private final RoomMapper roomMapper;
    private final AuthApi authApi;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Base64.Encoder codeEncoder = Base64.getUrlEncoder().withoutPadding();

    @Transactional
    public RoomInviteResponse createInvite(CurrentUser currentUser, UUID roomId) {
        requireOwner(currentUser, roomId);
        RoomInvite invite = roomInvites.findByRoomIdAndRevokedFalse(roomId)
                .orElseGet(() -> {
                    RoomInvite created = new RoomInvite();
                    created.setRoomId(roomId);
                    created.setCreatedBy(currentUser.getUserId());
                    created.setCode(generateCode());
                    return roomInvites.save(created);
                });
        return RoomInviteResponse.builder()
                .roomId(roomId)
                .code(invite.getCode())
                .build();
    }

    @Transactional
    public void revokeInvite(CurrentUser currentUser, UUID roomId) {
        requireOwner(currentUser, roomId);
        roomInvites.findByRoomIdAndRevokedFalse(roomId)
                .ifPresent(invite -> invite.setRevoked(true));
    }

    @Transactional
    public RoomResponse join(CurrentUser currentUser, String code) {
        String normalized = code == null ? "" : code.trim();
        RoomInvite invite = roomInvites.findByCodeAndRevokedFalse(normalized)
                .orElseThrow(() -> new RoomException(RoomException.Reason.INVALID_INVITE));
        Room room = rooms.findById(invite.getRoomId())
                .orElseThrow(() -> new RoomException(RoomException.Reason.ROOM_NOT_FOUND));
        if (!roomMembers.existsByRoomIdAndUserId(room.getId(), currentUser.getUserId())) {
            RoomMember member = new RoomMember();
            member.setRoomId(room.getId());
            member.setUserId(currentUser.getUserId());
            member.setRole(RoomRole.MEMBER);
            roomMembers.save(member);
        }
        return roomMapper.toResponse(room);
    }

    @Transactional(readOnly = true)
    public List<RoomMemberResponse> listMembers(CurrentUser currentUser, UUID roomId) {
        requireMember(currentUser, roomId);
        return roomMembers.findAllByRoomIdOrderByCreatedAtAsc(roomId).stream()
                .map(member -> RoomMemberResponse.builder()
                        .userId(member.getUserId())
                        .username(resolveUsername(member.getUserId()))
                        .role(member.getRole().name())
                        .self(member.getUserId().equals(currentUser.getUserId()))
                        .build())
                .toList();
    }

    @Transactional
    public void leave(CurrentUser currentUser, UUID roomId) {
        requireRoom(roomId);
        RoomMember member = roomMembers.findByRoomIdAndUserId(roomId, currentUser.getUserId())
                .orElseThrow(() -> new RoomException(RoomException.Reason.NOT_A_MEMBER));
        if (member.getRole() == RoomRole.OWNER) {
            throw new RoomException(RoomException.Reason.OWNER_CANNOT_LEAVE);
        }
        roomMembers.delete(member);
    }

    @Transactional
    public void removeMember(CurrentUser currentUser, UUID roomId, UUID targetUserId) {
        requireOwner(currentUser, roomId);
        RoomMember member = roomMembers.findByRoomIdAndUserId(roomId, targetUserId)
                .orElseThrow(() -> new RoomException(RoomException.Reason.MEMBER_NOT_FOUND));
        if (member.getRole() == RoomRole.OWNER) {
            throw new RoomException(RoomException.Reason.CANNOT_REMOVE_OWNER);
        }
        roomMembers.delete(member);
    }

    private String resolveUsername(UUID userId) {
        return authApi.findUser(userId).map(UserDto::getUsername).orElse(null);
    }

    private String generateCode() {
        byte[] bytes = new byte[INVITE_CODE_BYTES];
        secureRandom.nextBytes(bytes);
        return codeEncoder.encodeToString(bytes);
    }

    private Room requireRoom(UUID roomId) {
        return rooms.findById(roomId)
                .orElseThrow(() -> new RoomException(RoomException.Reason.ROOM_NOT_FOUND));
    }

    private void requireMember(CurrentUser currentUser, UUID roomId) {
        requireRoom(roomId);
        if (!roomMembers.existsByRoomIdAndUserId(roomId, currentUser.getUserId())) {
            throw new RoomException(RoomException.Reason.NOT_A_MEMBER);
        }
    }

    private void requireOwner(CurrentUser currentUser, UUID roomId) {
        requireRoom(roomId);
        RoomMember member = roomMembers.findByRoomIdAndUserId(roomId, currentUser.getUserId())
                .orElseThrow(() -> new RoomException(RoomException.Reason.NOT_A_MEMBER));
        if (member.getRole() != RoomRole.OWNER) {
            throw new RoomException(RoomException.Reason.NOT_OWNER);
        }
    }
}
