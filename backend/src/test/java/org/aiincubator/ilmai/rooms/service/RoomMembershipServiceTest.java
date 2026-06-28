package org.aiincubator.ilmai.rooms.service;

import org.aiincubator.ilmai.auth.AuthApi;
import org.aiincubator.ilmai.auth.UserDto;
import org.aiincubator.ilmai.auth.UserStatus;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoomMembershipServiceTest {

    @Mock RoomRepository rooms;
    @Mock RoomMemberRepository roomMembers;
    @Mock RoomInviteRepository roomInvites;
    @Mock AuthApi authApi;

    private RoomMembershipService service;

    private final UUID ownerId = UUID.randomUUID();
    private final UUID memberId = UUID.randomUUID();
    private final UUID roomId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new RoomMembershipService(rooms, roomMembers, roomInvites,
                Mappers.getMapper(RoomMapper.class), authApi);
    }

    @Test
    void createInvite_createsNewCodeWhenOwnerAndNoneActive() {
        stubRoom();
        stubMembership(ownerId, RoomRole.OWNER);
        when(roomInvites.findByRoomIdAndRevokedFalse(roomId)).thenReturn(Optional.empty());
        when(roomInvites.save(any(RoomInvite.class))).thenAnswer(inv -> inv.getArgument(0));

        RoomInviteResponse response = service.createInvite(new CurrentUser(ownerId), roomId);

        assertThat(response.getRoomId()).isEqualTo(roomId);
        assertThat(response.getCode()).isNotBlank();
        ArgumentCaptor<RoomInvite> captor = ArgumentCaptor.forClass(RoomInvite.class);
        verify(roomInvites).save(captor.capture());
        assertThat(captor.getValue().getCreatedBy()).isEqualTo(ownerId);
    }

    @Test
    void createInvite_reusesActiveInvite() {
        stubRoom();
        stubMembership(ownerId, RoomRole.OWNER);
        RoomInvite existing = new RoomInvite();
        existing.setRoomId(roomId);
        existing.setCode("EXISTING");
        when(roomInvites.findByRoomIdAndRevokedFalse(roomId)).thenReturn(Optional.of(existing));

        RoomInviteResponse response = service.createInvite(new CurrentUser(ownerId), roomId);

        assertThat(response.getCode()).isEqualTo("EXISTING");
        verify(roomInvites, never()).save(any(RoomInvite.class));
    }

    @Test
    void createInvite_rejectsNonOwner() {
        stubRoom();
        stubMembership(memberId, RoomRole.MEMBER);

        assertThatThrownBy(() -> service.createInvite(new CurrentUser(memberId), roomId))
                .isInstanceOf(RoomException.class)
                .extracting(e -> ((RoomException) e).getReason())
                .isEqualTo(RoomException.Reason.NOT_OWNER);
        verify(roomInvites, never()).save(any(RoomInvite.class));
    }

    @Test
    void join_addsMemberWhenNotAlreadyMember() {
        RoomInvite invite = new RoomInvite();
        invite.setRoomId(roomId);
        invite.setCode("CODE");
        when(roomInvites.findByCodeAndRevokedFalse("CODE")).thenReturn(Optional.of(invite));
        when(rooms.findById(roomId)).thenReturn(Optional.of(room()));
        when(roomMembers.existsByRoomIdAndUserId(roomId, memberId)).thenReturn(false);

        service.join(new CurrentUser(memberId), "  CODE  ");

        ArgumentCaptor<RoomMember> captor = ArgumentCaptor.forClass(RoomMember.class);
        verify(roomMembers).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(memberId);
        assertThat(captor.getValue().getRole()).isEqualTo(RoomRole.MEMBER);
    }

    @Test
    void join_isIdempotentWhenAlreadyMember() {
        RoomInvite invite = new RoomInvite();
        invite.setRoomId(roomId);
        invite.setCode("CODE");
        when(roomInvites.findByCodeAndRevokedFalse("CODE")).thenReturn(Optional.of(invite));
        when(rooms.findById(roomId)).thenReturn(Optional.of(room()));
        when(roomMembers.existsByRoomIdAndUserId(roomId, memberId)).thenReturn(true);

        service.join(new CurrentUser(memberId), "CODE");

        verify(roomMembers, never()).save(any(RoomMember.class));
    }

    @Test
    void join_rejectsInvalidCode() {
        when(roomInvites.findByCodeAndRevokedFalse("BAD")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.join(new CurrentUser(memberId), "BAD"))
                .isInstanceOf(RoomException.class)
                .extracting(e -> ((RoomException) e).getReason())
                .isEqualTo(RoomException.Reason.INVALID_INVITE);
    }

    @Test
    void leave_removesMemberMembership() {
        stubRoom();
        RoomMember member = stubMembership(memberId, RoomRole.MEMBER);

        service.leave(new CurrentUser(memberId), roomId);

        verify(roomMembers).delete(member);
    }

    @Test
    void leave_rejectsOwner() {
        stubRoom();
        stubMembership(ownerId, RoomRole.OWNER);

        assertThatThrownBy(() -> service.leave(new CurrentUser(ownerId), roomId))
                .isInstanceOf(RoomException.class)
                .extracting(e -> ((RoomException) e).getReason())
                .isEqualTo(RoomException.Reason.OWNER_CANNOT_LEAVE);
        verify(roomMembers, never()).delete(any(RoomMember.class));
    }

    @Test
    void removeMember_deletesTargetWhenOwnerActs() {
        stubRoom();
        stubMembership(ownerId, RoomRole.OWNER);
        RoomMember target = new RoomMember();
        target.setRoomId(roomId);
        target.setUserId(memberId);
        target.setRole(RoomRole.MEMBER);
        when(roomMembers.findByRoomIdAndUserId(roomId, memberId)).thenReturn(Optional.of(target));

        service.removeMember(new CurrentUser(ownerId), roomId, memberId);

        verify(roomMembers).delete(target);
    }

    @Test
    void removeMember_rejectsRemovingOwner() {
        stubRoom();
        RoomMember owner = stubMembership(ownerId, RoomRole.OWNER);
        when(roomMembers.findByRoomIdAndUserId(roomId, ownerId)).thenReturn(Optional.of(owner));

        assertThatThrownBy(() -> service.removeMember(new CurrentUser(ownerId), roomId, ownerId))
                .isInstanceOf(RoomException.class)
                .extracting(e -> ((RoomException) e).getReason())
                .isEqualTo(RoomException.Reason.CANNOT_REMOVE_OWNER);
        verify(roomMembers, never()).delete(any(RoomMember.class));
    }

    @Test
    void removeMember_rejectsNonOwnerCaller() {
        stubRoom();
        stubMembership(memberId, RoomRole.MEMBER);

        assertThatThrownBy(() -> service.removeMember(new CurrentUser(memberId), roomId, ownerId))
                .isInstanceOf(RoomException.class)
                .extracting(e -> ((RoomException) e).getReason())
                .isEqualTo(RoomException.Reason.NOT_OWNER);
    }

    @Test
    void listMembers_mapsUsernamesAndSelfFlag() {
        stubRoom();
        when(roomMembers.existsByRoomIdAndUserId(roomId, ownerId)).thenReturn(true);
        RoomMember owner = new RoomMember();
        owner.setRoomId(roomId);
        owner.setUserId(ownerId);
        owner.setRole(RoomRole.OWNER);
        RoomMember member = new RoomMember();
        member.setRoomId(roomId);
        member.setUserId(memberId);
        member.setRole(RoomRole.MEMBER);
        when(roomMembers.findAllByRoomIdOrderByCreatedAtAsc(roomId)).thenReturn(List.of(owner, member));
        when(authApi.findUser(ownerId)).thenReturn(Optional.of(new UserDto(ownerId, "owner@example.com", UserStatus.ACTIVE)));
        when(authApi.findUser(memberId)).thenReturn(Optional.of(new UserDto(memberId, "member@example.com", UserStatus.ACTIVE)));

        List<RoomMemberResponse> members = service.listMembers(new CurrentUser(ownerId), roomId);

        assertThat(members).extracting(RoomMemberResponse::getUsername)
                .containsExactly("owner@example.com", "member@example.com");
        assertThat(members).extracting(RoomMemberResponse::isSelf)
                .containsExactly(true, false);
        assertThat(members).extracting(RoomMemberResponse::getRole)
                .containsExactly("OWNER", "MEMBER");
    }

    private void stubRoom() {
        lenient().when(rooms.findById(roomId)).thenReturn(Optional.of(room()));
    }

    private Room room() {
        Room room = new Room();
        room.setId(roomId);
        room.setOwnerId(ownerId);
        room.setName("Study room");
        return room;
    }

    private RoomMember stubMembership(UUID userId, RoomRole role) {
        RoomMember member = new RoomMember();
        member.setRoomId(roomId);
        member.setUserId(userId);
        member.setRole(role);
        lenient().when(roomMembers.findByRoomIdAndUserId(roomId, userId)).thenReturn(Optional.of(member));
        lenient().when(roomMembers.existsByRoomIdAndUserId(roomId, userId)).thenReturn(true);
        return member;
    }
}
