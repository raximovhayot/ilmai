package org.aiincubator.ilmai.rooms.service;

import org.aiincubator.ilmai.auth.domain.User;
import org.aiincubator.ilmai.auth.UserStatus;
import org.aiincubator.ilmai.common.CurrentUser;
import org.aiincubator.ilmai.common.config.LocalizationConfig;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.i18n.LocaleContextHolder;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoomServiceTest {

    @Mock RoomRepository rooms;
    @Mock RoomMemberRepository roomMembers;
    @Mock QuotaService quotaService;
    @Mock ApplicationEventPublisher eventPublisher;

    private MessageService messages;
    private RoomService roomService;

    @BeforeEach
    void setUp() {
        messages = new MessageService(new LocalizationConfig().messageSource());
        roomService = new RoomService(rooms, roomMembers, messages, Mappers.getMapper(RoomMapper.class), quotaService, eventPublisher);
    }

    @AfterEach
    void clearLocale() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    void create_usesFirstNameWhenHintIsAvailable_inEnglish() {
        LocaleContextHolder.setLocale(Locale.ENGLISH);
        User user = newActiveUser();
        when(rooms.save(any(Room.class))).thenAnswer(inv -> {
            Room s = inv.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });

        Room created = roomService.create(user.getId(), "Aziza");

        ArgumentCaptor<Room> captor = ArgumentCaptor.forClass(Room.class);
        verify(rooms).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("Aziza's private space");
        assertThat(captor.getValue().getOwnerId()).isEqualTo(user.getId());
        assertThat(created.getName()).isEqualTo("Aziza's private space");
    }

    @Test
    void create_localizesDefaultNameInRussian() {
        LocaleContextHolder.setLocale(Locale.forLanguageTag("ru"));
        when(rooms.save(any(Room.class))).thenAnswer(inv -> inv.getArgument(0));

        roomService.create(newActiveUser().getId(), "Азиза");

        ArgumentCaptor<Room> captor = ArgumentCaptor.forClass(Room.class);
        verify(rooms).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("Личное пространство Азиза");
    }

    @Test
    void create_localizesDefaultNameInUzbek() {
        LocaleContextHolder.setLocale(Locale.forLanguageTag("uz"));
        when(rooms.save(any(Room.class))).thenAnswer(inv -> inv.getArgument(0));

        roomService.create(newActiveUser().getId(), "Aziza");

        ArgumentCaptor<Room> captor = ArgumentCaptor.forClass(Room.class);
        verify(rooms).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("Azizaning shaxsiy maydoni");
    }

    @Test
    void create_picksFirstWordOfMultiWordHint() {
        LocaleContextHolder.setLocale(Locale.ENGLISH);
        when(rooms.save(any(Room.class))).thenAnswer(inv -> inv.getArgument(0));

        roomService.create(newActiveUser().getId(), "Aziza Karimova");

        ArgumentCaptor<Room> captor = ArgumentCaptor.forClass(Room.class);
        verify(rooms).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("Aziza's private space");
    }

    @Test
    void create_fallsBackToGenericNameWhenHintIsNull() {
        LocaleContextHolder.setLocale(Locale.ENGLISH);
        when(rooms.save(any(Room.class))).thenAnswer(inv -> inv.getArgument(0));

        roomService.create(newActiveUser().getId(), null);

        ArgumentCaptor<Room> captor = ArgumentCaptor.forClass(Room.class);
        verify(rooms).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("My private space");
    }

    @Test
    void create_fallsBackToGenericNameWhenHintIsBlank() {
        LocaleContextHolder.setLocale(Locale.forLanguageTag("ru"));
        when(rooms.save(any(Room.class))).thenAnswer(inv -> inv.getArgument(0));

        roomService.create(newActiveUser().getId(), "   ");

        ArgumentCaptor<Room> captor = ArgumentCaptor.forClass(Room.class);
        verify(rooms).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("Моё личное пространство");
    }

    @Test
    void create_truncatesNameTo120Characters() {
        LocaleContextHolder.setLocale(Locale.ENGLISH);
        when(rooms.save(any(Room.class))).thenAnswer(inv -> inv.getArgument(0));
        String longFirstName = "A".repeat(200);

        roomService.create(newActiveUser().getId(), longFirstName);

        ArgumentCaptor<Room> captor = ArgumentCaptor.forClass(Room.class);
        verify(rooms).save(captor.capture());
        assertThat(captor.getValue().getName()).hasSize(120);
    }

    @Test
    void createExtra_createsNonPersonalRoomWhenPremium() {
        UUID userId = UUID.randomUUID();
        when(quotaService.isPremiumFeatureAllowed(userId, PremiumFeature.EXTRA_ROOM)).thenReturn(true);
        when(rooms.save(any(Room.class))).thenAnswer(inv -> {
            Room s = inv.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });

        RoomResponse response = roomService.createExtra(new CurrentUser(userId), "  AWS Study  ");

        ArgumentCaptor<Room> captor = ArgumentCaptor.forClass(Room.class);
        verify(rooms).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("AWS Study");
        assertThat(captor.getValue().getOwnerId()).isEqualTo(userId);
        assertThat(captor.getValue().isPersonal()).isFalse();
        assertThat(response.getName()).isEqualTo("AWS Study");
        verify(roomMembers).save(any());
    }

    @Test
    void createExtra_throwsPremiumRequiredWhenNotPremium() {
        UUID userId = UUID.randomUUID();
        CurrentUser currentUser = new CurrentUser(userId);
        when(quotaService.isPremiumFeatureAllowed(userId, PremiumFeature.EXTRA_ROOM)).thenReturn(false);

        assertThatThrownBy(() -> roomService.createExtra(currentUser, "AWS Study"))
                .isInstanceOf(RoomException.class)
                .extracting(e -> ((RoomException) e).getReason())
                .isEqualTo(RoomException.Reason.PREMIUM_REQUIRED);
        verify(rooms, never()).save(any(Room.class));
    }

    @Test
    void createExtra_rejectsBlankNameBeforeCheckingQuota() {
        CurrentUser currentUser = new CurrentUser(UUID.randomUUID());

        assertThatThrownBy(() -> roomService.createExtra(currentUser, "   "))
                .isInstanceOf(RoomException.class)
                .extracting(e -> ((RoomException) e).getReason())
                .isEqualTo(RoomException.Reason.NAME_BLANK);
        verify(quotaService, never()).isPremiumFeatureAllowed(any(UUID.class), eq(PremiumFeature.EXTRA_ROOM));
        verify(rooms, never()).save(any(Room.class));
    }

    @Test
    void getAll_returnsSingleElementWhenSpaceExists() {
        UUID userId = UUID.randomUUID();
        Room room = existingRoom(userId);
        room.setName("Aziza's private space");
        when(rooms.findAllByOwnerId(userId)).thenReturn(List.of(room));

        List<RoomResponse> response = roomService.getAll(new CurrentUser(userId));

        assertThat(response).hasSize(1);
        assertThat(response.get(0).getId()).isEqualTo(room.getId());
        assertThat(response.get(0).getName()).isEqualTo("Aziza's private space");
    }

    @Test
    void listForUser_returnsEmptyListWhenNoSpaceExists() {
        UUID userId = UUID.randomUUID();
        when(rooms.findAllByOwnerId(userId)).thenReturn(List.of());

        List<RoomResponse> response = roomService.getAll(new CurrentUser(userId));

        assertThat(response).isEmpty();
    }

    @Test
    void rename_updatesNameWhenValidAndOwned() {
        UUID userId = UUID.randomUUID();
        Room room = existingRoom(userId);
        when(rooms.findById(room.getId())).thenReturn(Optional.of(room));

        RoomResponse response = roomService.rename(new CurrentUser(userId), room.getId(), "  AWS Study  ");

        assertThat(response.getName()).isEqualTo("AWS Study");
        assertThat(room.getName()).isEqualTo("AWS Study");
    }

    @Test
    void rename_rejectsNull() {
        CurrentUser currentUser = new CurrentUser(UUID.randomUUID());
        UUID roomId = UUID.randomUUID();

        assertThatThrownBy(() -> roomService.rename(currentUser, roomId, null))
                .isInstanceOf(RoomException.class)
                .extracting(e -> ((RoomException) e).getReason())
                .isEqualTo(RoomException.Reason.NAME_BLANK);
        verify(rooms, never()).findById(any(UUID.class));
    }

    @Test
    void rename_rejectsBlank() {
        CurrentUser currentUser = new CurrentUser(UUID.randomUUID());
        UUID roomId = UUID.randomUUID();

        assertThatThrownBy(() -> roomService.rename(currentUser, roomId, "   "))
                .isInstanceOf(RoomException.class)
                .extracting(e -> ((RoomException) e).getReason())
                .isEqualTo(RoomException.Reason.NAME_BLANK);
        verify(rooms, never()).findById(any(UUID.class));
    }

    @Test
    void rename_rejectsOverlyLongName() {
        CurrentUser currentUser = new CurrentUser(UUID.randomUUID());
        UUID roomId = UUID.randomUUID();
        String tooLong = "x".repeat(121);

        assertThatThrownBy(() -> roomService.rename(currentUser, roomId, tooLong))
                .isInstanceOf(RoomException.class)
                .extracting(e -> ((RoomException) e).getReason())
                .isEqualTo(RoomException.Reason.NAME_BLANK);
        verify(rooms, never()).findById(any(UUID.class));
    }

    @Test
    void rename_throwsWhenSpaceMissing() {
        CurrentUser currentUser = new CurrentUser(UUID.randomUUID());
        UUID roomId = UUID.randomUUID();
        when(rooms.findById(roomId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roomService.rename(currentUser, roomId, "Anything"))
                .isInstanceOf(RoomException.class)
                .extracting(e -> ((RoomException) e).getReason())
                .isEqualTo(RoomException.Reason.ROOM_NOT_FOUND);
    }

    @Test
    void rename_throwsWhenSpaceBelongsToAnotherUser() {
        UUID ownerId = UUID.randomUUID();
        CurrentUser intruder = new CurrentUser(UUID.randomUUID());
        Room room = existingRoom(ownerId);
        when(rooms.findById(room.getId())).thenReturn(Optional.of(room));

        assertThatThrownBy(() -> roomService.rename(intruder, room.getId(), "Hijacked"))
                .isInstanceOf(RoomException.class)
                .extracting(e -> ((RoomException) e).getReason())
                .isEqualTo(RoomException.Reason.ROOM_NOT_FOUND);
        assertThat(room.getName()).isEqualTo("Original");
    }

    @Test
    void updateGoal_updatesFieldsAndPublishesEventWhenOwner() {
        UUID userId = UUID.randomUUID();
        Room room = existingRoom(userId);
        when(rooms.findById(room.getId())).thenReturn(Optional.of(room));
        when(roomMembers.findByRoomIdAndUserId(room.getId(), userId))
                .thenReturn(Optional.of(ownerMember(room.getId(), userId)));
        LocalDate target = LocalDate.now().plusDays(30);

        RoomResponse response = roomService.updateGoal(new CurrentUser(userId), room.getId(),
                "  Pass IELTS  ", target, 45);

        assertThat(room.getGoal()).isEqualTo("Pass IELTS");
        assertThat(room.getTargetDate()).isEqualTo(target);
        assertThat(room.getDailyStudyMinutes()).isEqualTo(45);
        assertThat(response.getGoal()).isEqualTo("Pass IELTS");
        assertThat(response.getTargetDate()).isEqualTo(target);
        assertThat(response.getDailyStudyMinutes()).isEqualTo(45);
        verify(eventPublisher).publishEvent(any(RoomGoalUpdatedEvent.class));
    }

    @Test
    void updateGoal_clearsGoalOnBlankAndSkipsEventWhenOnlyMinutesChange() {
        UUID userId = UUID.randomUUID();
        Room room = existingRoom(userId);
        room.setGoal("Old goal");
        when(rooms.findById(room.getId())).thenReturn(Optional.of(room));
        when(roomMembers.findByRoomIdAndUserId(room.getId(), userId))
                .thenReturn(Optional.of(ownerMember(room.getId(), userId)));

        roomService.updateGoal(new CurrentUser(userId), room.getId(), null, null, 20);

        assertThat(room.getGoal()).isEqualTo("Old goal");
        assertThat(room.getDailyStudyMinutes()).isEqualTo(20);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void updateGoal_rejectsPastTargetDate() {
        UUID userId = UUID.randomUUID();
        Room room = existingRoom(userId);
        when(rooms.findById(room.getId())).thenReturn(Optional.of(room));
        when(roomMembers.findByRoomIdAndUserId(room.getId(), userId))
                .thenReturn(Optional.of(ownerMember(room.getId(), userId)));
        CurrentUser currentUser = new CurrentUser(userId);
        UUID roomId = room.getId();
        LocalDate past = LocalDate.now().minusDays(1);

        assertThatThrownBy(() -> roomService.updateGoal(currentUser, roomId, "Goal", past, null))
                .isInstanceOf(RoomException.class)
                .extracting(e -> ((RoomException) e).getReason())
                .isEqualTo(RoomException.Reason.INVALID_TARGET_DATE);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void updateGoal_rejectsNonOwnerMember() {
        UUID ownerId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        Room room = existingRoom(ownerId);
        when(rooms.findById(room.getId())).thenReturn(Optional.of(room));
        RoomMember member = new RoomMember();
        member.setRoomId(room.getId());
        member.setUserId(memberId);
        member.setRole(RoomRole.MEMBER);
        when(roomMembers.findByRoomIdAndUserId(room.getId(), memberId)).thenReturn(Optional.of(member));
        CurrentUser currentUser = new CurrentUser(memberId);
        UUID roomId = room.getId();

        assertThatThrownBy(() -> roomService.updateGoal(currentUser, roomId, "Goal", null, null))
                .isInstanceOf(RoomException.class)
                .extracting(e -> ((RoomException) e).getReason())
                .isEqualTo(RoomException.Reason.NOT_OWNER);
    }

    @Test
    void updateGoal_rejectsNonMember() {
        UUID ownerId = UUID.randomUUID();
        UUID strangerId = UUID.randomUUID();
        Room room = existingRoom(ownerId);
        when(rooms.findById(room.getId())).thenReturn(Optional.of(room));
        when(roomMembers.findByRoomIdAndUserId(room.getId(), strangerId)).thenReturn(Optional.empty());
        CurrentUser currentUser = new CurrentUser(strangerId);
        UUID roomId = room.getId();

        assertThatThrownBy(() -> roomService.updateGoal(currentUser, roomId, "Goal", null, null))
                .isInstanceOf(RoomException.class)
                .extracting(e -> ((RoomException) e).getReason())
                .isEqualTo(RoomException.Reason.NOT_A_MEMBER);
    }

    private RoomMember ownerMember(UUID roomId, UUID userId) {
        RoomMember member = new RoomMember();
        member.setRoomId(roomId);
        member.setUserId(userId);
        member.setRole(RoomRole.OWNER);
        return member;
    }

    private User newActiveUser() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("user@example.com");
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }

    private Room existingRoom(UUID userId) {
        Room room = new Room();
        room.setId(UUID.randomUUID());
        room.setName("Original");
        room.setOwnerId(userId);
        return room;
    }
}
