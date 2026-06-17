package org.aiincubator.ilmai.gaps;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.List;

@Getter
@AllArgsConstructor
public final class GapsReportDto {

    private final OffsetDateTime generatedAt;
    private final int totalQuestionsAnswered;
    private final int correctCount;
    private final double overallAccuracy;
    private final String summary;
    private final List<GapItemDto> gaps;
    private final List<GapItemDto> strengths;
    private final String recommendedNext;
}
