package org.aiincubator.ilmai.agent;

import org.aiincubator.ilmai.common.CurrentUser;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface UserMemoryApi {

    List<UserFactDto> recentFacts(CurrentUser currentUser, int limit);

    int recordFacts(CurrentUser currentUser, List<String> facts);

    List<ReviewDueDto> dueReviews(CurrentUser currentUser, OffsetDateTime asOf);

    long countDueReviews(UUID userId, OffsetDateTime asOf);
}
