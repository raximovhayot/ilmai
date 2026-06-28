package org.aiincubator.ilmai.materials.service;


import org.aiincubator.ilmai.materials.MaterialStorageKeys;
import org.aiincubator.ilmai.materials.MaterialDeletedEvent;
import org.aiincubator.ilmai.materials.MaterialUploadedEvent;
import org.aiincubator.ilmai.common.CurrentUser;
import org.aiincubator.ilmai.common.quota.QuotaService;
import org.aiincubator.ilmai.common.storage.BlobStorage;
import org.aiincubator.ilmai.common.storage.StoredBlob;
import org.aiincubator.ilmai.materials.domain.Material;
import org.aiincubator.ilmai.materials.domain.MaterialRepository;
import org.aiincubator.ilmai.materials.MaterialStatus;
import org.aiincubator.ilmai.materials.domain.Topic;
import org.aiincubator.ilmai.materials.domain.TopicRepository;
import org.aiincubator.ilmai.materials.payload.MaterialResponse;
import org.aiincubator.ilmai.rooms.RoomsApi;
import org.aiincubator.ilmai.rooms.service.RoomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MaterialServiceTest {

    @Mock MaterialRepository materials;
    @Mock TopicRepository topics;
    @Mock BlobStorage storage;
    @Mock ApplicationEventPublisher publisher;
    @Mock QuotaService quotaService;
    @Mock RoomsApi roomsApi;

    private MaterialService materialService;

    @BeforeEach
    void setUp() {
        materialService = new MaterialService(
                materials, topics, Mappers.getMapper(MaterialMapper.class), storage, publisher, quotaService, roomsApi,
                Mappers.getMapper(TopicMapper.class));
        lenient().when(quotaService.materialUploadMaxBytes(any())).thenReturn(25L * 1024 * 1024);
    }

    @Test
    void upload_persistsFileWithTopicAndPublishesEvent() throws IOException {
        UUID userId = UUID.randomUUID();
        UUID spaceId = UUID.randomUUID();
        Topic topic = newTopic(spaceId);
        stubSpaceIds(userId, spaceId);
        when(topics.findByIdAndRoomIdIn(topic.getId(), List.of(spaceId))).thenReturn(Optional.of(topic));
        when(materials.save(any(Material.class))).thenAnswer(inv -> {
            Material m = inv.getArgument(0);
            m.setId(UUID.randomUUID());
            return m;
        });
        when(storage.put(anyString(), any(), anyLong(), anyString()))
                .thenAnswer(inv -> new StoredBlob(inv.getArgument(0), inv.getArgument(3), inv.getArgument(2)));

        MultipartFile file = new MockMultipartFile(
                "file", "notes.txt", "text/plain", "hello world".getBytes());

        MaterialResponse response = materialService.upload(
                new CurrentUser(userId), spaceId, topic.getId(), file);

        assertThat(response.getStatus()).isEqualTo("PENDING");
        assertThat(response.getTitle()).isEqualTo("notes.txt");
        assertThat(response.getTopicId()).isEqualTo(topic.getId());

        ArgumentCaptor<Material> captor = ArgumentCaptor.forClass(Material.class);
        verify(materials).save(captor.capture());
        Material saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(MaterialStatus.PENDING);
        assertThat(saved.getRoomId()).isEqualTo(spaceId);
        assertThat(saved.getTopic()).isEqualTo(topic);

        String expectedKey = spaceId + "/" + saved.getId();
        verify(storage).put(eq(expectedKey), any(), anyLong(), eq("text/plain"));
        verify(publisher).publishEvent(any(MaterialUploadedEvent.class));
    }

    @Test
    void upload_persistsFileWithoutTopic() throws IOException {
        UUID userId = UUID.randomUUID();
        UUID spaceId = UUID.randomUUID();
        stubSpaceIds(userId, spaceId);
        when(materials.save(any(Material.class))).thenAnswer(inv -> {
            Material m = inv.getArgument(0);
            m.setId(UUID.randomUUID());
            return m;
        });
        when(storage.put(anyString(), any(), anyLong(), anyString()))
                .thenAnswer(inv -> new StoredBlob(inv.getArgument(0), inv.getArgument(3), inv.getArgument(2)));

        MultipartFile file = new MockMultipartFile(
                "file", "notes.txt", "text/plain", "hello world".getBytes());

        MaterialResponse response = materialService.upload(
                new CurrentUser(userId), spaceId, null, file);

        assertThat(response.getTopicId()).isNull();
        ArgumentCaptor<Material> captor = ArgumentCaptor.forClass(Material.class);
        verify(materials).save(captor.capture());
        assertThat(captor.getValue().getTopic()).isNull();
        assertThat(captor.getValue().getRoomId()).isEqualTo(spaceId);
        verify(topics, never()).findByIdAndRoomIdIn(any(), any());
    }

    @Test
    void upload_rejectsWhenSpaceNotOwned() {
        UUID userId = UUID.randomUUID();
        UUID otherSpace = UUID.randomUUID();
        CurrentUser caller = new CurrentUser(userId);
        when(roomsApi.requireOwner(caller, otherSpace))
                .thenThrow(new RoomException(RoomException.Reason.NOT_OWNER));

        MultipartFile file = new MockMultipartFile("file", "n.txt", "text/plain", "x".getBytes());

        assertThatThrownBy(() -> materialService.upload(caller, otherSpace, null, file))
                .isInstanceOf(RoomException.class)
                .extracting(e -> ((RoomException) e).getReason())
                .isEqualTo(RoomException.Reason.NOT_OWNER);
        verify(materials, never()).save(any(Material.class));
    }

    @Test
    void upload_rejectsWhenTopicNotOwned() {
        UUID userId = UUID.randomUUID();
        UUID spaceId = UUID.randomUUID();
        UUID topicId = UUID.randomUUID();
        stubSpaceIds(userId, spaceId);
        when(topics.findByIdAndRoomIdIn(topicId, List.of(spaceId))).thenReturn(Optional.empty());

        MultipartFile file = new MockMultipartFile("file", "n.txt", "text/plain", "x".getBytes());

        assertThatThrownBy(() -> materialService.upload(
                new CurrentUser(userId), spaceId, topicId, file))
                .isInstanceOf(MaterialException.class)
                .extracting(e -> ((MaterialException) e).getReason())
                .isEqualTo(MaterialException.Reason.MATERIAL_TOPIC_NOT_FOUND);
        verify(materials, never()).save(any(Material.class));
    }

    @Test
    void upload_rejectsUnsupportedContentType() {
        UUID userId = UUID.randomUUID();
        UUID spaceId = UUID.randomUUID();
        Topic topic = newTopic(spaceId);
        stubSpaceIds(userId, spaceId);
        when(topics.findByIdAndRoomIdIn(topic.getId(), List.of(spaceId))).thenReturn(Optional.of(topic));

        MultipartFile file = new MockMultipartFile(
                "file", "icon.gif", "image/gif", "fake-gif".getBytes());

        assertThatThrownBy(() -> materialService.upload(
                new CurrentUser(userId), spaceId, topic.getId(), file))
                .isInstanceOf(MaterialException.class)
                .extracting(e -> ((MaterialException) e).getReason())
                .isEqualTo(MaterialException.Reason.MATERIAL_UNSUPPORTED_TYPE);
        verify(materials, never()).save(any(Material.class));
    }

    @Test
    void upload_rejectsWhenFreeTierLimitReached() {
        UUID userId = UUID.randomUUID();
        UUID spaceId = UUID.randomUUID();
        Topic topic = newTopic(spaceId);
        stubSpaceIds(userId, spaceId);
        when(topics.findByIdAndRoomIdIn(topic.getId(), List.of(spaceId))).thenReturn(Optional.of(topic));
        when(quotaService.materialUploadQuota(userId)).thenReturn(5);
        when(materials.countByRoomIdIn(List.of(spaceId))).thenReturn(5L);

        MultipartFile file = new MockMultipartFile(
                "file", "notes.txt", "text/plain", "x".getBytes());

        assertThatThrownBy(() -> materialService.upload(
                new CurrentUser(userId), spaceId, topic.getId(), file))
                .isInstanceOf(MaterialException.class)
                .extracting(e -> ((MaterialException) e).getReason())
                .isEqualTo(MaterialException.Reason.MATERIAL_UPLOAD_LIMIT);
        verify(materials, never()).save(any(Material.class));
        verify(publisher, never()).publishEvent(any());
    }

    @Test
    void upload_allowsWhenQuotaUnlimited() throws IOException {
        UUID userId = UUID.randomUUID();
        UUID spaceId = UUID.randomUUID();
        Topic topic = newTopic(spaceId);
        stubSpaceIds(userId, spaceId);
        when(topics.findByIdAndRoomIdIn(topic.getId(), List.of(spaceId))).thenReturn(Optional.of(topic));
        when(quotaService.materialUploadQuota(userId)).thenReturn(0);
        when(materials.save(any(Material.class))).thenAnswer(inv -> {
            Material m = inv.getArgument(0);
            m.setId(UUID.randomUUID());
            return m;
        });
        when(storage.put(anyString(), any(), anyLong(), anyString()))
                .thenAnswer(inv -> new StoredBlob(inv.getArgument(0), inv.getArgument(3), inv.getArgument(2)));

        MultipartFile file = new MockMultipartFile(
                "file", "notes.txt", "text/plain", "x".getBytes());

        MaterialResponse response = materialService.upload(
                new CurrentUser(userId), spaceId, topic.getId(), file);

        assertThat(response.getStatus()).isEqualTo("PENDING");
        verify(materials, never()).countByRoomIdIn(any());
    }

    @Test
    void upload_acceptsPdf() throws IOException {
        UUID userId = UUID.randomUUID();
        UUID spaceId = UUID.randomUUID();
        Topic topic = newTopic(spaceId);
        stubSpaceIds(userId, spaceId);
        when(topics.findByIdAndRoomIdIn(topic.getId(), List.of(spaceId))).thenReturn(Optional.of(topic));
        when(materials.save(any(Material.class))).thenAnswer(inv -> {
            Material m = inv.getArgument(0);
            m.setId(UUID.randomUUID());
            return m;
        });
        when(storage.put(anyString(), any(), anyLong(), anyString()))
                .thenAnswer(inv -> new StoredBlob(inv.getArgument(0), inv.getArgument(3), inv.getArgument(2)));

        MultipartFile file = new MockMultipartFile(
                "file", "lecture.pdf", "application/pdf", "%PDF-1.4 fake".getBytes());

        MaterialResponse response = materialService.upload(
                new CurrentUser(userId), spaceId, topic.getId(), file);

        assertThat(response.getStatus()).isEqualTo("PENDING");
        assertThat(response.getTitle()).isEqualTo("lecture.pdf");
        verify(storage).put(anyString(), any(), anyLong(), eq("application/pdf"));
        verify(publisher).publishEvent(any(MaterialUploadedEvent.class));
    }

    @Test
    void upload_acceptsAudio() throws IOException {
        UUID userId = UUID.randomUUID();
        UUID spaceId = UUID.randomUUID();
        Topic topic = newTopic(spaceId);
        stubSpaceIds(userId, spaceId);
        when(topics.findByIdAndRoomIdIn(topic.getId(), List.of(spaceId))).thenReturn(Optional.of(topic));
        when(materials.save(any(Material.class))).thenAnswer(inv -> {
            Material m = inv.getArgument(0);
            m.setId(UUID.randomUUID());
            return m;
        });
        when(storage.put(anyString(), any(), anyLong(), anyString()))
                .thenAnswer(inv -> new StoredBlob(inv.getArgument(0), inv.getArgument(3), inv.getArgument(2)));

        MultipartFile file = new MockMultipartFile(
                "file", "voice.mp3", "audio/mpeg", "fake-mp3".getBytes());

        MaterialResponse response = materialService.upload(
                new CurrentUser(userId), spaceId, topic.getId(), file);

        assertThat(response.getStatus()).isEqualTo("PENDING");
        verify(storage).put(anyString(), any(), anyLong(), eq("audio/mpeg"));
    }

    @Test
    void upload_acceptsImage() throws IOException {
        UUID userId = UUID.randomUUID();
        UUID spaceId = UUID.randomUUID();
        Topic topic = newTopic(spaceId);
        stubSpaceIds(userId, spaceId);
        when(topics.findByIdAndRoomIdIn(topic.getId(), List.of(spaceId))).thenReturn(Optional.of(topic));
        when(materials.save(any(Material.class))).thenAnswer(inv -> {
            Material m = inv.getArgument(0);
            m.setId(UUID.randomUUID());
            return m;
        });
        when(storage.put(anyString(), any(), anyLong(), anyString()))
                .thenAnswer(inv -> new StoredBlob(inv.getArgument(0), inv.getArgument(3), inv.getArgument(2)));

        MultipartFile file = new MockMultipartFile(
                "file", "diagram.png", "image/png", "fake-png".getBytes());

        MaterialResponse response = materialService.upload(
                new CurrentUser(userId), spaceId, topic.getId(), file);

        assertThat(response.getStatus()).isEqualTo("PENDING");
        verify(storage).put(anyString(), any(), anyLong(), eq("image/png"));
        verify(publisher).publishEvent(any(MaterialUploadedEvent.class));
    }

    @Test
    void list_returnsMaterialsForOwnedTopic() {
        UUID userId = UUID.randomUUID();
        UUID spaceId = UUID.randomUUID();
        Topic topic = newTopic(spaceId);
        Material a = newMaterial(spaceId, topic, "A");
        Material b = newMaterial(spaceId, topic, "B");
        stubSpaceIds(userId, spaceId);
        when(topics.findByIdAndRoomIdIn(topic.getId(), List.of(spaceId))).thenReturn(Optional.of(topic));
        when(materials.findAllByTopicIdAndRoomIdInOrderByCreatedAtDesc(topic.getId(), List.of(spaceId)))
                .thenReturn(List.of(a, b));

        assertThat(materialService.list(new CurrentUser(userId), null, topic.getId()))
                .extracting(MaterialResponse::getTitle)
                .containsExactly("A", "B");
    }

    @Test
    void list_returnsAllMaterialsWhenTopicIdNull() {
        UUID userId = UUID.randomUUID();
        UUID spaceId = UUID.randomUUID();
        Topic topic = newTopic(spaceId);
        Material a = newMaterial(spaceId, topic, "A");
        Material b = newMaterial(spaceId, null, "B");
        stubSpaceIds(userId, spaceId);
        when(materials.findAllByRoomIdInOrderByCreatedAtDesc(List.of(spaceId)))
                .thenReturn(List.of(a, b));

        assertThat(materialService.list(new CurrentUser(userId), null, null))
                .extracting(MaterialResponse::getTitle)
                .containsExactly("A", "B");
        verify(topics, never()).findByIdAndRoomIdIn(any(), any());
    }

    @Test
    void list_scopesToRequestedRoomForMember() {
        UUID userId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        CurrentUser caller = new CurrentUser(userId);
        Material a = newMaterial(roomId, null, "A");
        when(roomsApi.requireMember(caller, roomId)).thenReturn(null);
        when(materials.findAllByRoomIdInOrderByCreatedAtDesc(List.of(roomId)))
                .thenReturn(List.of(a));

        assertThat(materialService.list(caller, roomId, null))
                .extracting(MaterialResponse::getTitle)
                .containsExactly("A");
        verify(roomsApi, never()).findRoomIdsForUser(any());
    }

    @Test
    void list_rejectsNonMemberRoom() {
        UUID userId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        CurrentUser caller = new CurrentUser(userId);
        when(roomsApi.requireMember(caller, roomId))
                .thenThrow(new RoomException(RoomException.Reason.NOT_A_MEMBER));

        assertThatThrownBy(() -> materialService.list(caller, roomId, null))
                .isInstanceOf(RoomException.class)
                .extracting(e -> ((RoomException) e).getReason())
                .isEqualTo(RoomException.Reason.NOT_A_MEMBER);
        verify(materials, never()).findAllByRoomIdInOrderByCreatedAtDesc(any());
    }

    @Test
    void list_throwsWhenTopicNotOwned() {
        UUID userId = UUID.randomUUID();
        UUID spaceId = UUID.randomUUID();
        UUID topicId = UUID.randomUUID();
        stubSpaceIds(userId, spaceId);
        when(topics.findByIdAndRoomIdIn(topicId, List.of(spaceId))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> materialService.list(new CurrentUser(userId), null, topicId))
                .isInstanceOf(MaterialException.class)
                .extracting(e -> ((MaterialException) e).getReason())
                .isEqualTo(MaterialException.Reason.MATERIAL_TOPIC_NOT_FOUND);
    }

    @Test
    void get_returnsOwnedMaterial() {
        UUID userId = UUID.randomUUID();
        UUID spaceId = UUID.randomUUID();
        Topic topic = newTopic(spaceId);
        Material material = newMaterial(spaceId, topic, "Cloud notes");
        stubSpaceIds(userId, spaceId);
        when(materials.findByIdAndRoomIdIn(material.getId(), List.of(spaceId)))
                .thenReturn(Optional.of(material));

        MaterialResponse response = materialService.get(new CurrentUser(userId), material.getId());

        assertThat(response.getTitle()).isEqualTo("Cloud notes");
        assertThat(response.getTopicId()).isEqualTo(topic.getId());
    }

    @Test
    void get_throwsWhenMaterialNotOwned() {
        UUID userId = UUID.randomUUID();
        UUID spaceId = UUID.randomUUID();
        UUID materialId = UUID.randomUUID();
        stubSpaceIds(userId, spaceId);
        when(materials.findByIdAndRoomIdIn(materialId, List.of(spaceId))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> materialService.get(new CurrentUser(userId), materialId))
                .isInstanceOf(MaterialException.class)
                .extracting(e -> ((MaterialException) e).getReason())
                .isEqualTo(MaterialException.Reason.MATERIAL_NOT_FOUND);
    }

    @Test
    void delete_removesRowAndStorageObject() throws IOException {
        UUID userId = UUID.randomUUID();
        UUID spaceId = UUID.randomUUID();
        Topic topic = newTopic(spaceId);
        Material material = newMaterial(spaceId, topic, "Cloud notes");
        stubSpaceIds(userId, spaceId);
        when(materials.findByIdAndRoomIdIn(material.getId(), List.of(spaceId)))
                .thenReturn(Optional.of(material));

        materialService.delete(new CurrentUser(userId), material.getId());

        verify(materials).delete(material);
        verify(storage, times(1)).delete(MaterialStorageKeys.forCoordinates(spaceId, material.getId()));
    }

    @Test
    void delete_publishesMaterialDeletedEvent() throws IOException {
        UUID userId = UUID.randomUUID();
        UUID spaceId = UUID.randomUUID();
        Topic topic = newTopic(spaceId);
        Material material = newMaterial(spaceId, topic, "Cloud notes");
        stubSpaceIds(userId, spaceId);
        when(materials.findByIdAndRoomIdIn(material.getId(), List.of(spaceId)))
                .thenReturn(Optional.of(material));

        materialService.delete(new CurrentUser(userId), material.getId());

        ArgumentCaptor<MaterialDeletedEvent> captor = ArgumentCaptor.forClass(MaterialDeletedEvent.class);
        verify(publisher).publishEvent(captor.capture());
        MaterialDeletedEvent event = captor.getValue();
        assertThat(event.getMaterialId()).isEqualTo(material.getId());
        assertThat(event.getUserId()).isEqualTo(userId);
    }

    @Test
    void delete_throwsWhenNotOwned() throws IOException {
        UUID userId = UUID.randomUUID();
        UUID spaceId = UUID.randomUUID();
        UUID materialId = UUID.randomUUID();
        stubSpaceIds(userId, spaceId);
        when(materials.findByIdAndRoomIdIn(materialId, List.of(spaceId))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> materialService.delete(new CurrentUser(userId), materialId))
                .isInstanceOf(MaterialException.class)
                .extracting(e -> ((MaterialException) e).getReason())
                .isEqualTo(MaterialException.Reason.MATERIAL_NOT_FOUND);
        verify(materials, never()).delete(any(Material.class));
        verify(storage, never()).delete(anyString());
    }

    @Test
    void deleteAll_removesAndPublishesForEach() throws IOException {
        UUID userId = UUID.randomUUID();
        UUID spaceId = UUID.randomUUID();
        Topic topic = newTopic(spaceId);
        Material a = newMaterial(spaceId, topic, "A");
        Material b = newMaterial(spaceId, topic, "B");

        materialService.deleteAll(new CurrentUser(userId), List.of(a, b));

        verify(materials).delete(a);
        verify(materials).delete(b);
        verify(storage).delete(MaterialStorageKeys.forCoordinates(spaceId, a.getId()));
        verify(storage).delete(MaterialStorageKeys.forCoordinates(spaceId, b.getId()));
        verify(publisher, times(2)).publishEvent(any(MaterialDeletedEvent.class));
    }

    @Test
    void move_toTopicSetsTopicForOwnedMaterial() {
        UUID userId = UUID.randomUUID();
        UUID spaceId = UUID.randomUUID();
        Topic target = newTopic(spaceId);
        Material material = newMaterial(spaceId, null, "Loose note");
        stubSpaceIds(userId, spaceId);
        when(materials.findByIdAndRoomIdIn(material.getId(), List.of(spaceId)))
                .thenReturn(Optional.of(material));
        when(topics.findByIdAndRoomIdIn(target.getId(), List.of(spaceId)))
                .thenReturn(Optional.of(target));

        MaterialResponse response = materialService.move(new CurrentUser(userId), material.getId(), target.getId());

        assertThat(response.getTopicId()).isEqualTo(target.getId());
        assertThat(material.getTopic()).isEqualTo(target);
    }

    @Test
    void move_toRootClearsTopic() {
        UUID userId = UUID.randomUUID();
        UUID spaceId = UUID.randomUUID();
        Topic topic = newTopic(spaceId);
        Material material = newMaterial(spaceId, topic, "Note");
        stubSpaceIds(userId, spaceId);
        when(materials.findByIdAndRoomIdIn(material.getId(), List.of(spaceId)))
                .thenReturn(Optional.of(material));

        MaterialResponse response = materialService.move(new CurrentUser(userId), material.getId(), null);

        assertThat(response.getTopicId()).isNull();
        assertThat(material.getTopic()).isNull();
        verify(topics, never()).findByIdAndRoomIdIn(any(), any());
    }

    @Test
    void move_throwsWhenMaterialNotOwned() {
        UUID userId = UUID.randomUUID();
        UUID spaceId = UUID.randomUUID();
        UUID materialId = UUID.randomUUID();
        stubSpaceIds(userId, spaceId);
        when(materials.findByIdAndRoomIdIn(materialId, List.of(spaceId))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> materialService.move(new CurrentUser(userId), materialId, null))
                .isInstanceOf(MaterialException.class)
                .extracting(e -> ((MaterialException) e).getReason())
                .isEqualTo(MaterialException.Reason.MATERIAL_NOT_FOUND);
    }

    @Test
    void move_throwsWhenTargetTopicNotOwned() {
        UUID userId = UUID.randomUUID();
        UUID spaceId = UUID.randomUUID();
        UUID topicId = UUID.randomUUID();
        Material material = newMaterial(spaceId, null, "Note");
        stubSpaceIds(userId, spaceId);
        when(materials.findByIdAndRoomIdIn(material.getId(), List.of(spaceId)))
                .thenReturn(Optional.of(material));
        when(topics.findByIdAndRoomIdIn(topicId, List.of(spaceId))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> materialService.move(new CurrentUser(userId), material.getId(), topicId))
                .isInstanceOf(MaterialException.class)
                .extracting(e -> ((MaterialException) e).getReason())
                .isEqualTo(MaterialException.Reason.MATERIAL_TOPIC_NOT_FOUND);
    }

    private void stubSpaceIds(UUID userId, UUID spaceId) {
        when(roomsApi.findRoomIdsForUser(userId)).thenReturn(List.of(spaceId));
    }

    private Topic newTopic(UUID spaceId) {
        Topic topic = new Topic();
        topic.setId(UUID.randomUUID());
        topic.setRoomId(spaceId);
        topic.setName("Cloud");
        return topic;
    }

    private Material newMaterial(UUID spaceId, Topic topic, String title) {
        Material material = new Material();
        material.setId(UUID.randomUUID());
        material.setRoomId(spaceId);
        material.setTopic(topic);
        material.setTitle(title);
        material.setStatus(MaterialStatus.READY);
        material.setContentType("text/plain");
        material.setSizeBytes(10L);
        return material;
    }
}
