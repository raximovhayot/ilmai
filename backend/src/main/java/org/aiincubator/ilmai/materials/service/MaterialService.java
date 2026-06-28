package org.aiincubator.ilmai.materials.service;



import org.aiincubator.ilmai.materials.MaterialDeletedEvent;
import org.aiincubator.ilmai.materials.MaterialStorageKeys;
import org.aiincubator.ilmai.materials.MaterialUploadedEvent;
import lombok.RequiredArgsConstructor;
import org.aiincubator.ilmai.common.CurrentUser;
import org.aiincubator.ilmai.common.quota.QuotaService;
import org.aiincubator.ilmai.common.storage.BlobStorage;
import org.aiincubator.ilmai.materials.domain.Material;
import org.aiincubator.ilmai.materials.domain.MaterialRepository;
import org.aiincubator.ilmai.materials.MaterialStatus;
import org.aiincubator.ilmai.materials.domain.Topic;
import org.aiincubator.ilmai.materials.domain.TopicRepository;
import org.aiincubator.ilmai.materials.payload.MaterialResponse;
import org.aiincubator.ilmai.materials.payload.SpaceContentsResponse;
import org.aiincubator.ilmai.materials.payload.TopicResponse;
import org.aiincubator.ilmai.rooms.RoomDto;
import org.aiincubator.ilmai.rooms.RoomsApi;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MaterialService {

    private static final int TITLE_MAX_LENGTH = 255;
    private static final Set<String> ALLOWED_UPLOAD_TYPES = Set.of(
            "text/plain",
            "text/markdown",
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "audio/mpeg",
            "audio/wav",
            "audio/mp4",
            "image/png",
            "image/jpeg"
    );

    private static final int MAX_PAGE_SIZE = 100;

    private final MaterialRepository materials;
    private final TopicRepository topics;
    private final MaterialMapper materialMapper;
    private final BlobStorage storage;
    private final ApplicationEventPublisher publisher;
    private final QuotaService quotaService;
    private final RoomsApi roomsApi;
    private final TopicMapper topicMapper;

    @Transactional
    public MaterialResponse upload(CurrentUser currentUser, UUID spaceId, UUID topicId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new MaterialException(MaterialException.Reason.MATERIAL_UNSUPPORTED_TYPE, "");
        }
        roomsApi.requireOwner(currentUser, spaceId);
        List<UUID> spaceIds = requireSpaceIds(currentUser);

        Topic topic = null;
        if (topicId != null) {
            topic = topics.findByIdAndRoomIdIn(topicId, List.of(spaceId))
                    .orElseThrow(() -> new MaterialException(MaterialException.Reason.MATERIAL_TOPIC_NOT_FOUND));
        }

        int quota = quotaService.materialUploadQuota(currentUser.getUserId());
        if (quota > 0) {
            long owned = materials.countByRoomIdIn(spaceIds);
            if (owned >= quota) {
                throw new MaterialException(MaterialException.Reason.MATERIAL_UPLOAD_LIMIT, quota);
            }
        }

        long maxBytes = quotaService.materialUploadMaxBytes(currentUser.getUserId());
        validateUploadType(file.getContentType());
        validateSize(file.getSize(), maxBytes);

        Material material = new Material();
        material.setRoomId(spaceId);
        material.setTopic(topic);
        material.setStatus(MaterialStatus.PENDING);
        material.setContentType(file.getContentType());
        material.setSizeBytes(file.getSize());
        material.setTitle(resolveTitle(file.getOriginalFilename()));

        Material saved = materials.save(material);
        String storageKey = MaterialStorageKeys.forCoordinates(spaceId, saved.getId());

        try (InputStream in = file.getInputStream()) {
            storage.put(storageKey, in, file.getSize(), file.getContentType());
        } catch (IOException ex) {
            throw new MaterialException(MaterialException.Reason.MATERIAL_STORAGE_FAILED);
        }

        publisher.publishEvent(new MaterialUploadedEvent(saved.getId(), currentUser.getUserId()));
        return materialMapper.toResponse(saved);
    }

    @Transactional
    public Material ingest(CurrentUser currentUser, byte[] content, String filename, String contentType) {
        if (content == null || content.length == 0) {
            throw new MaterialException(MaterialException.Reason.MATERIAL_UNSUPPORTED_TYPE, "");
        }
        RoomDto primary = roomsApi.findPersonalForUser(currentUser.getUserId())
                .orElseThrow(() -> new MaterialException(MaterialException.Reason.MATERIAL_SPACE_NOT_FOUND));
        UUID spaceId = primary.getId();
        List<UUID> spaceIds = requireSpaceIds(currentUser);

        int quota = quotaService.materialUploadQuota(currentUser.getUserId());
        if (quota > 0) {
            long owned = materials.countByRoomIdIn(spaceIds);
            if (owned >= quota) {
                throw new MaterialException(MaterialException.Reason.MATERIAL_UPLOAD_LIMIT, quota);
            }
        }

        long maxBytes = quotaService.materialUploadMaxBytes(currentUser.getUserId());
        validateUploadType(contentType);
        validateSize(content.length, maxBytes);

        Material material = new Material();
        material.setRoomId(spaceId);
        material.setStatus(MaterialStatus.PENDING);
        material.setContentType(contentType);
        material.setSizeBytes((long) content.length);
        material.setTitle(resolveTitle(filename));

        Material saved = materials.save(material);
        String storageKey = MaterialStorageKeys.forCoordinates(spaceId, saved.getId());

        try (InputStream in = new ByteArrayInputStream(content)) {
            storage.put(storageKey, in, content.length, contentType);
        } catch (IOException ex) {
            throw new MaterialException(MaterialException.Reason.MATERIAL_STORAGE_FAILED);
        }

        publisher.publishEvent(new MaterialUploadedEvent(saved.getId(), currentUser.getUserId()));
        return saved;
    }

    @Transactional(readOnly = true)
    public List<MaterialResponse> list(CurrentUser currentUser, UUID topicId) {
        List<UUID> spaceIds = requireSpaceIds(currentUser);
        List<Material> found;
        if (topicId != null) {
            topics.findByIdAndRoomIdIn(topicId, spaceIds)
                    .orElseThrow(() -> new MaterialException(MaterialException.Reason.MATERIAL_TOPIC_NOT_FOUND));
            found = materials.findAllByTopicIdAndRoomIdInOrderByCreatedAtDesc(topicId, spaceIds);
        } else {
            found = materials.findAllByRoomIdInOrderByCreatedAtDesc(spaceIds);
        }
        return found.stream()
                .map(materialMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public SpaceContentsResponse contents(CurrentUser currentUser, int page, int size) {
        List<UUID> spaceIds = requireSpaceIds(currentUser);
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Slice<Material> slice = materials.findByTopicIsNullAndRoomIdInOrderByCreatedAtDesc(
                spaceIds, PageRequest.of(safePage, safeSize));
        List<TopicResponse> topicList = safePage == 0
                ? topics.findAllByRoomIdInOrderByCreatedAtAsc(spaceIds).stream()
                        .map(topicMapper::toResponse)
                        .toList()
                : List.of();
        List<MaterialResponse> items = slice.getContent().stream()
                .map(materialMapper::toResponse)
                .toList();
        return SpaceContentsResponse.builder()
                .topics(topicList)
                .items(items)
                .page(safePage)
                .size(safeSize)
                .hasMore(slice.hasNext())
                .build();
    }

    @Transactional
    public MaterialResponse move(CurrentUser currentUser, UUID materialId, UUID targetTopicId) {
        List<UUID> spaceIds = requireSpaceIds(currentUser);
        Material material = materials.findByIdAndRoomIdIn(materialId, spaceIds)
                .orElseThrow(() -> new MaterialException(MaterialException.Reason.MATERIAL_NOT_FOUND));
        roomsApi.requireOwner(currentUser, material.getRoomId());
        if (targetTopicId == null) {
            material.setTopic(null);
        } else {
            Topic topic = topics.findByIdAndRoomIdIn(targetTopicId, List.of(material.getRoomId()))
                    .orElseThrow(() -> new MaterialException(MaterialException.Reason.MATERIAL_TOPIC_NOT_FOUND));
            material.setTopic(topic);
        }
        return materialMapper.toResponse(material);
    }

    @Transactional(readOnly = true)
    public MaterialResponse get(CurrentUser currentUser, UUID materialId) {
        List<UUID> spaceIds = requireSpaceIds(currentUser);
        return materials.findByIdAndRoomIdIn(materialId, spaceIds)
                .map(materialMapper::toResponse)
                .orElseThrow(() -> new MaterialException(MaterialException.Reason.MATERIAL_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public RawMaterial openRaw(CurrentUser currentUser, UUID materialId) {
        List<UUID> spaceIds = requireSpaceIds(currentUser);
        Material material = materials.findByIdAndRoomIdIn(materialId, spaceIds)
                .orElseThrow(() -> new MaterialException(MaterialException.Reason.MATERIAL_NOT_FOUND));
        String storageKey = MaterialStorageKeys.forCoordinates(material.getRoomId(), material.getId());
        try (InputStream in = storage.open(storageKey)) {
            byte[] content = in.readAllBytes();
            return new RawMaterial(content, material.getContentType(), material.getTitle());
        } catch (IOException ex) {
            throw new MaterialException(MaterialException.Reason.MATERIAL_STORAGE_FAILED);
        }
    }

    @Transactional
    public void delete(CurrentUser currentUser, UUID materialId) {
        List<UUID> spaceIds = requireSpaceIds(currentUser);
        Material material = materials.findByIdAndRoomIdIn(materialId, spaceIds)
                .orElseThrow(() -> new MaterialException(MaterialException.Reason.MATERIAL_NOT_FOUND));
        roomsApi.requireOwner(currentUser, material.getRoomId());
        deleteInternal(material, currentUser.getUserId());
    }

    @Transactional
    public void deleteAll(CurrentUser currentUser, List<Material> targets) {
        for (Material material : targets) {
            deleteInternal(material, currentUser.getUserId());
        }
    }

    private void deleteInternal(Material material, UUID userId) {
        UUID deletedId = material.getId();
        String storageKey = MaterialStorageKeys.forCoordinates(material.getRoomId(), deletedId);
        materials.delete(material);
        try {
            storage.delete(storageKey);
        } catch (IOException ignored) {
        }
        publisher.publishEvent(new MaterialDeletedEvent(deletedId, userId));
    }

    private List<UUID> requireSpaceIds(CurrentUser currentUser) {
        List<UUID> spaceIds = roomsApi.findRoomIdsForUser(currentUser.getUserId());
        if (spaceIds.isEmpty()) {
            throw new MaterialException(MaterialException.Reason.MATERIAL_SPACE_NOT_FOUND);
        }
        return spaceIds;
    }

    private void validateUploadType(String contentType) {
        if (contentType == null || !ALLOWED_UPLOAD_TYPES.contains(contentType.toLowerCase().split(";")[0].trim())) {
            throw new MaterialException(MaterialException.Reason.MATERIAL_UNSUPPORTED_TYPE, contentType);
        }
    }

    private void validateSize(long size, long maxBytes) {
        if (size > maxBytes) {
            throw new MaterialException(MaterialException.Reason.MATERIAL_TOO_LARGE, maxBytes);
        }
    }

    private String resolveTitle(String fallbackFilename) {
        if (fallbackFilename != null && !fallbackFilename.isBlank()) {
            String safe = fallbackFilename.trim();
            return safe.length() > TITLE_MAX_LENGTH ? safe.substring(0, TITLE_MAX_LENGTH) : safe;
        }
        throw new MaterialException(MaterialException.Reason.MATERIAL_TITLE_BLANK);
    }
}
