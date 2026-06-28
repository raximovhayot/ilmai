package org.aiincubator.ilmai.materials.service;

import lombok.RequiredArgsConstructor;
import org.aiincubator.ilmai.common.CurrentUser;
import org.aiincubator.ilmai.materials.MaterialDto;
import org.aiincubator.ilmai.materials.MaterialStatus;
import org.aiincubator.ilmai.materials.MaterialsApi;
import org.aiincubator.ilmai.materials.TopicDto;
import org.aiincubator.ilmai.materials.domain.Material;
import org.aiincubator.ilmai.materials.domain.MaterialRepository;
import org.aiincubator.ilmai.materials.domain.TopicRepository;
import org.aiincubator.ilmai.rooms.RoomsApi;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DefaultMaterialsApi implements MaterialsApi {

    private final MaterialRepository materials;
    private final TopicRepository topics;
    private final RoomsApi roomsApi;
    private final MaterialsApiMapper mapper;
    private final MaterialService materialService;

    @Override
    @Transactional(readOnly = true)
    public boolean hasReadyMaterialsForUser(UUID userId) {
        List<UUID> spaceIds = roomsApi.findRoomIdsForUser(userId);
        if (spaceIds.isEmpty()) {
            return false;
        }
        return materials.countByRoomIdInAndStatus(spaceIds, MaterialStatus.READY) > 0;
    }

    @Override
    @Transactional(readOnly = true)
    public List<MaterialDto> findReadyForUser(UUID userId) {
        List<UUID> spaceIds = roomsApi.findRoomIdsForUser(userId);
        if (spaceIds.isEmpty()) {
            return List.of();
        }
        return materials.findAllByRoomIdInAndStatusOrderByCreatedAtDesc(spaceIds, MaterialStatus.READY)
                .stream()
                .map(mapper::toDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MaterialDto> findOwnedByUser(UUID materialId, UUID userId) {
        List<UUID> spaceIds = roomsApi.findRoomIdsForUser(userId);
        if (spaceIds.isEmpty()) {
            return Optional.empty();
        }
        return materials.findByIdAndRoomIdIn(materialId, spaceIds).map(mapper::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MaterialDto> findById(UUID materialId) {
        return materials.findById(materialId).map(mapper::toDto);
    }

    @Override
    @Transactional
    public void updateStatus(UUID materialId, MaterialStatus status) {
        Material managed = materials.findById(materialId)
                .orElseThrow(() -> new MaterialException(MaterialException.Reason.MATERIAL_NOT_FOUND));
        managed.setStatus(status);
    }

    @Override
    @Transactional
    public void flushStatus(UUID materialId, MaterialStatus status) {
        Material managed = materials.findById(materialId)
                .orElseThrow(() -> new MaterialException(MaterialException.Reason.MATERIAL_NOT_FOUND));
        managed.setStatus(status);
        materials.saveAndFlush(managed);
    }

    @Override
    @Transactional
    public MaterialDto ingestUpload(UUID userId, String filename, String contentType, byte[] content) {
        Material saved = materialService.ingest(new CurrentUser(userId), content, filename, contentType);
        return mapper.toDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TopicDto> findTopicOwnedByUser(UUID topicId, UUID userId) {
        List<UUID> spaceIds = roomsApi.findRoomIdsForUser(userId);
        if (spaceIds.isEmpty()) {
            return Optional.empty();
        }
        return topics.findByIdAndRoomIdIn(topicId, spaceIds).map(mapper::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TopicDto> findAllTopicsByUser(UUID userId) {
        List<UUID> spaceIds = roomsApi.findRoomIdsForUser(userId);
        if (spaceIds.isEmpty()) {
            return List.of();
        }
        return topics.findAllByRoomIdInOrderByCreatedAtAsc(spaceIds)
                .stream()
                .map(mapper::toDto)
                .toList();
    }
}
