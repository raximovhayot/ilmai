package org.aiincubator.ilmai.spaces.service;

import org.aiincubator.ilmai.auth.domain.User;
import org.aiincubator.ilmai.auth.UserStatus;
import org.aiincubator.ilmai.common.CurrentUser;
import org.aiincubator.ilmai.common.config.LocalizationConfig;
import org.aiincubator.ilmai.common.i18n.MessageService;
import org.aiincubator.ilmai.spaces.domain.Space;
import org.aiincubator.ilmai.spaces.domain.SpaceRepository;
import org.aiincubator.ilmai.spaces.payload.SpaceResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.i18n.LocaleContextHolder;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpaceServiceTest {

    @Mock SpaceRepository spaces;

    private MessageService messages;
    private SpaceService spaceService;

    @BeforeEach
    void setUp() {
        messages = new MessageService(new LocalizationConfig().messageSource());
        spaceService = new SpaceService(spaces, messages, Mappers.getMapper(SpaceMapper.class));
    }

    @AfterEach
    void clearLocale() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    void create_usesFirstNameWhenHintIsAvailable_inEnglish() {
        LocaleContextHolder.setLocale(Locale.ENGLISH);
        User user = newActiveUser();
        when(spaces.save(any(Space.class))).thenAnswer(inv -> {
            Space s = inv.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });

        Space created = spaceService.create(user.getId(), "Aziza");

        ArgumentCaptor<Space> captor = ArgumentCaptor.forClass(Space.class);
        verify(spaces).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("Aziza's private space");
        assertThat(captor.getValue().getUserId()).isEqualTo(user.getId());
        assertThat(created.getName()).isEqualTo("Aziza's private space");
    }

    @Test
    void create_localizesDefaultNameInRussian() {
        LocaleContextHolder.setLocale(Locale.forLanguageTag("ru"));
        when(spaces.save(any(Space.class))).thenAnswer(inv -> inv.getArgument(0));

        spaceService.create(newActiveUser().getId(), "Азиза");

        ArgumentCaptor<Space> captor = ArgumentCaptor.forClass(Space.class);
        verify(spaces).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("Личное пространство Азиза");
    }

    @Test
    void create_localizesDefaultNameInUzbek() {
        LocaleContextHolder.setLocale(Locale.forLanguageTag("uz"));
        when(spaces.save(any(Space.class))).thenAnswer(inv -> inv.getArgument(0));

        spaceService.create(newActiveUser().getId(), "Aziza");

        ArgumentCaptor<Space> captor = ArgumentCaptor.forClass(Space.class);
        verify(spaces).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("Azizaning shaxsiy maydoni");
    }

    @Test
    void create_picksFirstWordOfMultiWordHint() {
        LocaleContextHolder.setLocale(Locale.ENGLISH);
        when(spaces.save(any(Space.class))).thenAnswer(inv -> inv.getArgument(0));

        spaceService.create(newActiveUser().getId(), "Aziza Karimova");

        ArgumentCaptor<Space> captor = ArgumentCaptor.forClass(Space.class);
        verify(spaces).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("Aziza's private space");
    }

    @Test
    void create_fallsBackToGenericNameWhenHintIsNull() {
        LocaleContextHolder.setLocale(Locale.ENGLISH);
        when(spaces.save(any(Space.class))).thenAnswer(inv -> inv.getArgument(0));

        spaceService.create(newActiveUser().getId(), null);

        ArgumentCaptor<Space> captor = ArgumentCaptor.forClass(Space.class);
        verify(spaces).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("My private space");
    }

    @Test
    void create_fallsBackToGenericNameWhenHintIsBlank() {
        LocaleContextHolder.setLocale(Locale.forLanguageTag("ru"));
        when(spaces.save(any(Space.class))).thenAnswer(inv -> inv.getArgument(0));

        spaceService.create(newActiveUser().getId(), "   ");

        ArgumentCaptor<Space> captor = ArgumentCaptor.forClass(Space.class);
        verify(spaces).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("Моё личное пространство");
    }

    @Test
    void create_truncatesNameTo120Characters() {
        LocaleContextHolder.setLocale(Locale.ENGLISH);
        when(spaces.save(any(Space.class))).thenAnswer(inv -> inv.getArgument(0));
        String longFirstName = "A".repeat(200);

        spaceService.create(newActiveUser().getId(), longFirstName);

        ArgumentCaptor<Space> captor = ArgumentCaptor.forClass(Space.class);
        verify(spaces).save(captor.capture());
        assertThat(captor.getValue().getName()).hasSize(120);
    }

    @Test
    void getAll_returnsSingleElementWhenSpaceExists() {
        UUID userId = UUID.randomUUID();
        Space space = existingSpace(userId);
        space.setName("Aziza's private space");
        when(spaces.findAllByUserId(userId)).thenReturn(List.of(space));

        List<SpaceResponse> response = spaceService.getAll(new CurrentUser(userId));

        assertThat(response).hasSize(1);
        assertThat(response.get(0).getId()).isEqualTo(space.getId());
        assertThat(response.get(0).getName()).isEqualTo("Aziza's private space");
    }

    @Test
    void listForUser_returnsEmptyListWhenNoSpaceExists() {
        UUID userId = UUID.randomUUID();
        when(spaces.findAllByUserId(userId)).thenReturn(List.of());

        List<SpaceResponse> response = spaceService.getAll(new CurrentUser(userId));

        assertThat(response).isEmpty();
    }

    @Test
    void rename_updatesNameWhenValidAndOwned() {
        UUID userId = UUID.randomUUID();
        Space space = existingSpace(userId);
        when(spaces.findById(space.getId())).thenReturn(Optional.of(space));

        SpaceResponse response = spaceService.rename(new CurrentUser(userId), space.getId(), "  AWS Study  ");

        assertThat(response.getName()).isEqualTo("AWS Study");
        assertThat(space.getName()).isEqualTo("AWS Study");
    }

    @Test
    void rename_rejectsNull() {
        CurrentUser currentUser = new CurrentUser(UUID.randomUUID());
        UUID spaceId = UUID.randomUUID();

        assertThatThrownBy(() -> spaceService.rename(currentUser, spaceId, null))
                .isInstanceOf(SpaceException.class)
                .extracting(e -> ((SpaceException) e).getReason())
                .isEqualTo(SpaceException.Reason.NAME_BLANK);
        verify(spaces, never()).findById(any(UUID.class));
    }

    @Test
    void rename_rejectsBlank() {
        CurrentUser currentUser = new CurrentUser(UUID.randomUUID());
        UUID spaceId = UUID.randomUUID();

        assertThatThrownBy(() -> spaceService.rename(currentUser, spaceId, "   "))
                .isInstanceOf(SpaceException.class)
                .extracting(e -> ((SpaceException) e).getReason())
                .isEqualTo(SpaceException.Reason.NAME_BLANK);
        verify(spaces, never()).findById(any(UUID.class));
    }

    @Test
    void rename_rejectsOverlyLongName() {
        CurrentUser currentUser = new CurrentUser(UUID.randomUUID());
        UUID spaceId = UUID.randomUUID();
        String tooLong = "x".repeat(121);

        assertThatThrownBy(() -> spaceService.rename(currentUser, spaceId, tooLong))
                .isInstanceOf(SpaceException.class)
                .extracting(e -> ((SpaceException) e).getReason())
                .isEqualTo(SpaceException.Reason.NAME_BLANK);
        verify(spaces, never()).findById(any(UUID.class));
    }

    @Test
    void rename_throwsWhenSpaceMissing() {
        CurrentUser currentUser = new CurrentUser(UUID.randomUUID());
        UUID spaceId = UUID.randomUUID();
        when(spaces.findById(spaceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> spaceService.rename(currentUser, spaceId, "Anything"))
                .isInstanceOf(SpaceException.class)
                .extracting(e -> ((SpaceException) e).getReason())
                .isEqualTo(SpaceException.Reason.SPACE_NOT_FOUND);
    }

    @Test
    void rename_throwsWhenSpaceBelongsToAnotherUser() {
        UUID ownerId = UUID.randomUUID();
        CurrentUser intruder = new CurrentUser(UUID.randomUUID());
        Space space = existingSpace(ownerId);
        when(spaces.findById(space.getId())).thenReturn(Optional.of(space));

        assertThatThrownBy(() -> spaceService.rename(intruder, space.getId(), "Hijacked"))
                .isInstanceOf(SpaceException.class)
                .extracting(e -> ((SpaceException) e).getReason())
                .isEqualTo(SpaceException.Reason.SPACE_NOT_FOUND);
        assertThat(space.getName()).isEqualTo("Original");
    }

    private User newActiveUser() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("user@example.com");
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }

    private Space existingSpace(UUID userId) {
        Space space = new Space();
        space.setId(UUID.randomUUID());
        space.setName("Original");
        space.setUserId(userId);
        return space;
    }
}
