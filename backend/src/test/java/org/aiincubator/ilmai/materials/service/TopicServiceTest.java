package org.aiincubator.ilmai.materials.service;

import org.aiincubator.ilmai.common.CurrentUser;
import org.aiincubator.ilmai.materials.domain.Material;
import org.aiincubator.ilmai.materials.domain.MaterialRepository;
import org.aiincubator.ilmai.materials.domain.Topic;
import org.aiincubator.ilmai.materials.domain.TopicRepository;
import org.aiincubator.ilmai.materials.payload.TopicResponse;
import org.aiincubator.ilmai.rooms.RoomDto;
import org.aiincubator.ilmai.rooms.RoomsApi;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TopicServiceTest {

    @Mock TopicRepository topics;
    @Mock MaterialRepository materials;
    @Mock MaterialService materialService;
    @Mock RoomsApi roomsApi;

    private TopicService topicService;

    @BeforeEach
    void setUp() {
        topicService = new TopicService(topics, materials, materialService, roomsApi, Mappers.getMapper(TopicMapper.class));
    }

    @Test
    void create_persistsTopicWithTrimmedNameAndCallerSpace() {
        UUID userId = UUID.randomUUID();
        RoomDto space = newRoomDto(userId);
        when(roomsApi.findPersonalForUser(userId)).thenReturn(Optional.of(space));
        when(topics.existsByRoomIdAndNameIgnoreCase(space.getId(), "AWS Study")).thenReturn(false);
        when(topics.save(any(Topic.class))).thenAnswer(inv -> {
            Topic t = inv.getArgument(0);
            t.setId(UUID.randomUUID());
            return t;
        });

        TopicResponse response = topicService.create(new CurrentUser(userId), "  AWS Study  ");

        ArgumentCaptor<Topic> captor = ArgumentCaptor.forClass(Topic.class);
        verify(topics).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("AWS Study");
        assertThat(captor.getValue().getRoomId()).isEqualTo(space.getId());
        assertThat(response.getName()).isEqualTo("AWS Study");
        assertThat(response.getSpaceId()).isEqualTo(space.getId());
    }

    @Test
    void create_rejectsBlankName() {
        CurrentUser caller = new CurrentUser(UUID.randomUUID());

        assertThatThrownBy(() -> topicService.create(caller, "   "))
                .isInstanceOf(TopicException.class)
                .extracting(e -> ((TopicException) e).getReason())
                .isEqualTo(TopicException.Reason.TOPIC_NAME_BLANK);
        verify(roomsApi, never()).findPersonalForUser(any(UUID.class));
        verify(topics, never()).save(any(Topic.class));
    }

    @Test
    void create_rejectsOverlyLongName() {
        CurrentUser caller = new CurrentUser(UUID.randomUUID());
        String tooLong = "x".repeat(121);

        assertThatThrownBy(() -> topicService.create(caller, tooLong))
                .isInstanceOf(TopicException.class)
                .extracting(e -> ((TopicException) e).getReason())
                .isEqualTo(TopicException.Reason.TOPIC_NAME_BLANK);
        verify(topics, never()).save(any(Topic.class));
    }

    @Test
    void create_throwsWhenUserHasNoSpace() {
        UUID userId = UUID.randomUUID();
        when(roomsApi.findPersonalForUser(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> topicService.create(new CurrentUser(userId), "Anything"))
                .isInstanceOf(TopicException.class)
                .extracting(e -> ((TopicException) e).getReason())
                .isEqualTo(TopicException.Reason.SPACE_NOT_FOUND);
    }

    @Test
    void create_throwsWhenNameAlreadyTakenCaseInsensitive() {
        UUID userId = UUID.randomUUID();
        RoomDto space = newRoomDto(userId);
        when(roomsApi.findPersonalForUser(userId)).thenReturn(Optional.of(space));
        when(topics.existsByRoomIdAndNameIgnoreCase(space.getId(), "AWS Study")).thenReturn(true);

        assertThatThrownBy(() -> topicService.create(new CurrentUser(userId), "AWS Study"))
                .isInstanceOf(TopicException.class)
                .extracting(e -> ((TopicException) e).getReason())
                .isEqualTo(TopicException.Reason.TOPIC_NAME_TAKEN);
        verify(topics, never()).save(any(Topic.class));
    }

    @Test
    void list_returnsTopicsOwnedByCaller() {
        UUID userId = UUID.randomUUID();
        UUID spaceId = UUID.randomUUID();
        Topic a = newTopic(spaceId, "Cloud");
        Topic b = newTopic(spaceId, "Ottoman History");
        when(roomsApi.findRoomIdsForUser(userId)).thenReturn(List.of(spaceId));
        when(topics.findAllByRoomIdInOrderByCreatedAtAsc(List.of(spaceId))).thenReturn(List.of(a, b));

        List<TopicResponse> response = topicService.list(new CurrentUser(userId));

        assertThat(response).extracting(TopicResponse::getName)
                .containsExactly("Cloud", "Ottoman History");
        assertThat(response).extracting(TopicResponse::getSpaceId)
                .containsOnly(spaceId);
    }

    @Test
    void list_returnsEmptyWhenUserHasNoSpaces() {
        UUID userId = UUID.randomUUID();
        when(roomsApi.findRoomIdsForUser(userId)).thenReturn(List.of());

        assertThat(topicService.list(new CurrentUser(userId))).isEmpty();
        verify(topics, never()).findAllByRoomIdInOrderByCreatedAtAsc(any());
    }

    @Test
    void rename_updatesNameWhenOwnedAndNotTaken() {
        UUID userId = UUID.randomUUID();
        UUID spaceId = UUID.randomUUID();
        Topic topic = newTopic(spaceId, "Old");
        when(roomsApi.findRoomIdsForUser(userId)).thenReturn(List.of(spaceId));
        when(topics.findByIdAndRoomIdIn(topic.getId(), List.of(spaceId))).thenReturn(Optional.of(topic));
        when(topics.existsByRoomIdAndNameIgnoreCaseAndIdNot(eq(spaceId), anyString(), eq(topic.getId())))
                .thenReturn(false);

        TopicResponse response = topicService.rename(new CurrentUser(userId), topic.getId(), "  New  ");

        assertThat(response.getName()).isEqualTo("New");
        assertThat(topic.getName()).isEqualTo("New");
    }

    @Test
    void rename_throwsWhenTopicNotOwnedByCaller() {
        UUID userId = UUID.randomUUID();
        UUID spaceId = UUID.randomUUID();
        UUID topicId = UUID.randomUUID();
        when(roomsApi.findRoomIdsForUser(userId)).thenReturn(List.of(spaceId));
        when(topics.findByIdAndRoomIdIn(topicId, List.of(spaceId))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> topicService.rename(new CurrentUser(userId), topicId, "Anything"))
                .isInstanceOf(TopicException.class)
                .extracting(e -> ((TopicException) e).getReason())
                .isEqualTo(TopicException.Reason.TOPIC_NOT_FOUND);
    }

    @Test
    void rename_throwsOnDuplicateName() {
        UUID userId = UUID.randomUUID();
        UUID spaceId = UUID.randomUUID();
        Topic topic = newTopic(spaceId, "Old");
        when(roomsApi.findRoomIdsForUser(userId)).thenReturn(List.of(spaceId));
        when(topics.findByIdAndRoomIdIn(topic.getId(), List.of(spaceId))).thenReturn(Optional.of(topic));
        when(topics.existsByRoomIdAndNameIgnoreCaseAndIdNot(eq(spaceId), anyString(), eq(topic.getId())))
                .thenReturn(true);

        assertThatThrownBy(() ->
                topicService.rename(new CurrentUser(userId), topic.getId(), "Cloud"))
                .isInstanceOf(TopicException.class)
                .extracting(e -> ((TopicException) e).getReason())
                .isEqualTo(TopicException.Reason.TOPIC_NAME_TAKEN);
        assertThat(topic.getName()).isEqualTo("Old");
    }

    @Test
    void delete_withoutCascade_detachesMaterialsAndRemovesTopic() {
        UUID userId = UUID.randomUUID();
        UUID spaceId = UUID.randomUUID();
        Topic topic = newTopic(spaceId, "Old");
        when(roomsApi.findRoomIdsForUser(userId)).thenReturn(List.of(spaceId));
        when(topics.findByIdAndRoomIdIn(topic.getId(), List.of(spaceId))).thenReturn(Optional.of(topic));

        topicService.delete(new CurrentUser(userId), topic.getId(), false);

        verify(materials).detachFromTopic(topic.getId());
        verify(materials, never()).findAllByTopicId(any());
        verify(materialService, never()).deleteAll(any(), any());
        verify(topics).delete(topic);
    }

    @Test
    void delete_withCascade_deletesMaterialsThenTopic() {
        UUID userId = UUID.randomUUID();
        UUID spaceId = UUID.randomUUID();
        Topic topic = newTopic(spaceId, "Old");
        Material a = new Material();
        Material b = new Material();
        when(roomsApi.findRoomIdsForUser(userId)).thenReturn(List.of(spaceId));
        when(topics.findByIdAndRoomIdIn(topic.getId(), List.of(spaceId))).thenReturn(Optional.of(topic));
        when(materials.findAllByTopicId(topic.getId())).thenReturn(List.of(a, b));

        topicService.delete(new CurrentUser(userId), topic.getId(), true);

        verify(materialService).deleteAll(any(CurrentUser.class), eq(List.of(a, b)));
        verify(materials, never()).detachFromTopic(any());
        verify(topics).delete(topic);
    }

    @Test
    void delete_throwsWhenTopicNotOwnedByCaller() {
        UUID userId = UUID.randomUUID();
        UUID spaceId = UUID.randomUUID();
        UUID topicId = UUID.randomUUID();
        when(roomsApi.findRoomIdsForUser(userId)).thenReturn(List.of(spaceId));
        when(topics.findByIdAndRoomIdIn(topicId, List.of(spaceId))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> topicService.delete(new CurrentUser(userId), topicId, false))
                .isInstanceOf(TopicException.class)
                .extracting(e -> ((TopicException) e).getReason())
                .isEqualTo(TopicException.Reason.TOPIC_NOT_FOUND);
        verify(topics, never()).delete(any(Topic.class));
    }

    private RoomDto newRoomDto(UUID userId) {
        return new RoomDto(UUID.randomUUID(), userId, "My private space", true);
    }

    private Topic newTopic(UUID spaceId, String name) {
        Topic topic = new Topic();
        topic.setId(UUID.randomUUID());
        topic.setRoomId(spaceId);
        topic.setName(name);
        return topic;
    }
}
