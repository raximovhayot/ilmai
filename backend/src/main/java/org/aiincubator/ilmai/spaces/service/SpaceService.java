package org.aiincubator.ilmai.spaces.service;

import lombok.RequiredArgsConstructor;
import org.aiincubator.ilmai.common.CurrentUser;
import org.aiincubator.ilmai.common.i18n.MessageService;
import org.aiincubator.ilmai.spaces.domain.Space;
import org.aiincubator.ilmai.spaces.domain.SpaceRepository;
import org.aiincubator.ilmai.spaces.payload.SpaceResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SpaceService {

    private static final int NAME_MAX_LENGTH = 120;
    private static final String KEY_DEFAULT_NAME_WITH_OWNER = "space.defaultName.withOwner";
    private static final String KEY_DEFAULT_NAME_GENERIC = "space.defaultName.generic";

    private final SpaceRepository spaces;
    private final MessageService messages;
    private final SpaceMapper spaceMapper;

    @Transactional
    public Space create(UUID userId, String firstNameHint) {
        String defaultName = computeDefaultName(firstNameHint);
        Space space = new Space();
        space.setUserId(userId);
        space.setName(defaultName);
        return spaces.save(space);
    }

    @Transactional(readOnly = true)
    public List<SpaceResponse> getAll(CurrentUser currentUser) {
        return spaces.findAllByUserId(currentUser.getUserId()).stream()
                .map(spaceMapper::toResponse)
                .toList();
    }

    @Transactional
    public SpaceResponse rename(CurrentUser currentUser, UUID spaceId, String newName) {
        String trimmed = newName == null ? null : newName.trim();
        if (trimmed == null || trimmed.isEmpty() || trimmed.length() > NAME_MAX_LENGTH) {
            throw new SpaceException(SpaceException.Reason.NAME_BLANK);
        }
        Space space = spaces.findById(spaceId)
                .orElseThrow(() -> new SpaceException(SpaceException.Reason.SPACE_NOT_FOUND));
        if (!currentUser.getUserId().equals(space.getUserId())) {
            throw new SpaceException(SpaceException.Reason.SPACE_NOT_FOUND);
        }
        space.setName(trimmed);
        return spaceMapper.toResponse(space);
    }

    private String computeDefaultName(String firstNameHint) {
        String firstName = extractFirstName(firstNameHint);
        String resolved;
        if (firstName == null) {
            resolved = messages.get(KEY_DEFAULT_NAME_GENERIC);
        } else {
            resolved = messages.get(KEY_DEFAULT_NAME_WITH_OWNER, new Object[]{firstName});
        }
        if (resolved.length() > NAME_MAX_LENGTH) {
            return resolved.substring(0, NAME_MAX_LENGTH);
        }
        return resolved;
    }

    private String extractFirstName(String hint) {
        if (hint == null) {
            return null;
        }
        String trimmed = hint.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        int firstWhitespace = indexOfWhitespace(trimmed);
        return firstWhitespace < 0 ? trimmed : trimmed.substring(0, firstWhitespace);
    }

    private int indexOfWhitespace(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isWhitespace(value.charAt(i))) {
                return i;
            }
        }
        return -1;
    }
}
