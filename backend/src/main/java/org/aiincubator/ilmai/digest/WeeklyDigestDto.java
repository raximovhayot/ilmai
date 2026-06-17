package org.aiincubator.ilmai.digest;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@AllArgsConstructor
public final class WeeklyDigestDto {

    private final UUID id;
    private final UUID userId;
    private final String isoWeek;
    private final DigestVariant variant;
    private final OffsetDateTime generatedAt;
    private final int activeDays;
    private final int quizzes;
    private final int answered;
    private final int correct;
    private final Integer avgScore;
    private final int planDone;
    private final int planTotal;
    private final int streakNow;
    private final Integer daysUntilDeadline;
    private final List<String> topGaps;
    private final String whereYouStand;
    private final List<String> focusNextWeek;
}
