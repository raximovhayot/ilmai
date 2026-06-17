package org.aiincubator.ilmai.agent.usermemory.service;

import lombok.RequiredArgsConstructor;
import org.aiincubator.ilmai.common.CurrentUser;
import org.aiincubator.ilmai.agent.ReviewDueDto;
import org.aiincubator.ilmai.agent.UserFactDto;
import org.aiincubator.ilmai.agent.UserMemoryApi;
import org.aiincubator.ilmai.agent.usermemory.domain.ReviewQueueEntry;
import org.aiincubator.ilmai.agent.usermemory.domain.ReviewQueueRepository;
import org.aiincubator.ilmai.agent.usermemory.domain.ReviewStatus;
import org.aiincubator.ilmai.agent.usermemory.domain.UserMemoryFact;
import org.aiincubator.ilmai.agent.usermemory.domain.UserMemoryFactRepository;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DefaultUserMemoryApi implements UserMemoryApi {

    private final UserMemoryFactRepository facts;
    private final ReviewQueueRepository reviewQueue;
    private final UserMemoryApiMapper userMemoryApiMapper;

    @Override
    @Transactional(readOnly = true)
    public List<UserFactDto> recentFacts(CurrentUser currentUser, int limit) {
        if (currentUser == null || limit <= 0) {
            return List.of();
        }
        List<UserMemoryFact> rows =
                facts.findByUserIdOrderByCreatedAtDesc(currentUser.getUserId(), Limit.of(limit));
        return userMemoryApiMapper.toDtoList(rows);
    }

    @Override
    @Transactional
    public int recordFacts(CurrentUser currentUser, List<String> newFacts) {
        if (currentUser == null || newFacts == null || newFacts.isEmpty()) {
            return 0;
        }
        List<UserMemoryFact> toSave = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String raw : newFacts) {
            if (raw == null) {
                continue;
            }
            String content = raw.trim();
            if (content.isEmpty() || !seen.add(content)) {
                continue;
            }
            UserMemoryFact fact = new UserMemoryFact();
            fact.setUserId(currentUser.getUserId());
            fact.setContent(content);
            toSave.add(fact);
        }
        if (toSave.isEmpty()) {
            return 0;
        }
        facts.saveAll(toSave);
        return toSave.size();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReviewDueDto> dueReviews(CurrentUser currentUser, OffsetDateTime asOf) {
        if (currentUser == null || asOf == null) {
            return List.of();
        }
        List<ReviewQueueEntry> rows = reviewQueue
                .findByUserIdAndStatusAndNextReviewAtLessThanEqualOrderByNextReviewAtAsc(
                        currentUser.getUserId(), ReviewStatus.ACTIVE, asOf);
        return userMemoryApiMapper.toReviewDtoList(rows);
    }

    @Override
    @Transactional(readOnly = true)
    public long countDueReviews(UUID userId, OffsetDateTime asOf) {
        if (userId == null || asOf == null) {
            return 0;
        }
        return reviewQueue.countByUserIdAndStatusAndNextReviewAtLessThanEqual(
                userId, ReviewStatus.ACTIVE, asOf);
    }
}
