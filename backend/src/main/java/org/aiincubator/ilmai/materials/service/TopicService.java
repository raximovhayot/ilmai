package org.aiincubator.ilmai.materials.service;

import lombok.RequiredArgsConstructor;
import org.aiincubator.ilmai.common.CurrentUser;
import org.aiincubator.ilmai.materials.domain.Material;
import org.aiincubator.ilmai.materials.domain.MaterialRepository;
import org.aiincubator.ilmai.materials.domain.Topic;
import org.aiincubator.ilmai.materials.domain.TopicRepository;
import org.aiincubator.ilmai.materials.payload.TopicResponse;
import org.aiincubator.ilmai.spaces.SpaceDto;
import org.aiincubator.ilmai.spaces.SpacesApi;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TopicService {

    private static final int NAME_MAX_LENGTH = 120;

    private final TopicRepository topics;
    private final MaterialRepository materials;
    private final MaterialService materialService;
    private final SpacesApi spacesApi;
    private final TopicMapper topicMapper;

    @Transactional
    public TopicResponse create(CurrentUser currentUser, String name) {
        String trimmed = normalizeName(name);
        SpaceDto space = resolveCallerSpace(currentUser);
        if (topics.existsBySpaceIdAndNameIgnoreCase(space.getId(), trimmed)) {
            throw new TopicException(TopicException.Reason.TOPIC_NAME_TAKEN, trimmed);
        }
        Topic topic = new Topic();
        topic.setSpaceId(space.getId());
        topic.setName(trimmed);
        return topicMapper.toResponse(topics.save(topic));
    }

    @Transactional(readOnly = true)
    public List<TopicResponse> list(CurrentUser currentUser) {
        List<UUID> spaceIds = spacesApi.findSpaceIdsForUser(currentUser.getUserId());
        if (spaceIds.isEmpty()) {
            return List.of();
        }
        return topics.findAllBySpaceIdInOrderByCreatedAtAsc(spaceIds).stream()
                .map(topicMapper::toResponse)
                .toList();
    }

    @Transactional
    public TopicResponse rename(CurrentUser currentUser, UUID topicId, String name) {
        String trimmed = normalizeName(name);
        Topic topic = loadOwnedTopic(currentUser, topicId);
        if (topics.existsBySpaceIdAndNameIgnoreCaseAndIdNot(topic.getSpaceId(), trimmed, topic.getId())) {
            throw new TopicException(TopicException.Reason.TOPIC_NAME_TAKEN, trimmed);
        }
        topic.setName(trimmed);
        return topicMapper.toResponse(topic);
    }

    @Transactional
    public void delete(CurrentUser currentUser, UUID topicId, boolean deleteMaterials) {
        Topic topic = loadOwnedTopic(currentUser, topicId);
        if (deleteMaterials) {
            List<Material> owned = materials.findAllByTopicId(topic.getId());
            materialService.deleteAll(currentUser, owned);
        } else {
            materials.detachFromTopic(topic.getId());
        }
        topics.delete(topic);
    }

    Topic loadOwnedTopic(CurrentUser currentUser, UUID topicId) {
        List<UUID> spaceIds = spacesApi.findSpaceIdsForUser(currentUser.getUserId());
        if (spaceIds.isEmpty()) {
            throw new TopicException(TopicException.Reason.TOPIC_NOT_FOUND);
        }
        return topics.findByIdAndSpaceIdIn(topicId, spaceIds)
                .orElseThrow(() -> new TopicException(TopicException.Reason.TOPIC_NOT_FOUND));
    }

    private String normalizeName(String name) {
        String trimmed = name == null ? null : name.trim();
        if (trimmed == null || trimmed.isEmpty() || trimmed.length() > NAME_MAX_LENGTH) {
            throw new TopicException(TopicException.Reason.TOPIC_NAME_BLANK);
        }
        return trimmed;
    }

    private SpaceDto resolveCallerSpace(CurrentUser currentUser) {
        return spacesApi.findPrimaryForUser(currentUser.getUserId())
                .orElseThrow(() -> new TopicException(TopicException.Reason.SPACE_NOT_FOUND));
    }
}
