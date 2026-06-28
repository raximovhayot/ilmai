package org.aiincubator.ilmai.rooms.service;

import org.aiincubator.ilmai.rooms.RoomGoalUpdatedEvent;
import org.aiincubator.ilmai.rooms.domain.Room;
import org.aiincubator.ilmai.rooms.domain.RoomRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultRoomsApiTest {

    @Mock RoomRepository rooms;
    @Mock org.aiincubator.ilmai.rooms.domain.RoomMemberRepository roomMembers;
    @Mock RoomsApiMapper roomsApiMapper;
    @Mock ApplicationEventPublisher eventPublisher;

    @InjectMocks DefaultRoomsApi api;

    private Room personalRoom(UUID owner) {
        Room room = new Room();
        room.setOwnerId(owner);
        room.setPersonal(true);
        return room;
    }

    @Test
    void applyGoalPatchPublishesRoomGoalUpdatedEventForTheUser() {
        UUID user = UUID.randomUUID();
        when(rooms.findFirstByOwnerIdAndPersonalTrueOrderByCreatedAtAsc(user))
                .thenReturn(Optional.of(personalRoom(user)));

        api.applyGoalPatch(user, "IELTS", null, null);

        ArgumentCaptor<RoomGoalUpdatedEvent> captor = ArgumentCaptor.forClass(RoomGoalUpdatedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(user);
    }

    @Test
    void applyGoalPatchDoesNotPublishWhenNothingChanges() {
        UUID user = UUID.randomUUID();
        when(rooms.findFirstByOwnerIdAndPersonalTrueOrderByCreatedAtAsc(user))
                .thenReturn(Optional.of(personalRoom(user)));

        api.applyGoalPatch(user, null, null, 30);

        verify(eventPublisher, never()).publishEvent(any(RoomGoalUpdatedEvent.class));
    }

    @Test
    void applyGoalPatchRejectsPastTargetDate() {
        UUID user = UUID.randomUUID();
        when(rooms.findFirstByOwnerIdAndPersonalTrueOrderByCreatedAtAsc(user))
                .thenReturn(Optional.of(personalRoom(user)));

        assertThatThrownBy(() -> api.applyGoalPatch(user, "Goal", LocalDate.now().minusDays(1), null))
                .isInstanceOf(RoomException.class);
        verify(eventPublisher, never()).publishEvent(any(RoomGoalUpdatedEvent.class));
    }

    @Test
    void applyGoalPatchReturnsEmptyWhenNoPersonalRoom() {
        UUID user = UUID.randomUUID();
        when(rooms.findFirstByOwnerIdAndPersonalTrueOrderByCreatedAtAsc(user))
                .thenReturn(Optional.empty());

        assertThat(api.applyGoalPatch(user, "Goal", null, null)).isEmpty();
        verify(eventPublisher, never()).publishEvent(any(RoomGoalUpdatedEvent.class));
    }
}
