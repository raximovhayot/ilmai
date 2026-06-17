package org.aiincubator.ilmai.gaps.payload;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GapsReportResponse {

    private OffsetDateTime generatedAt;
    private int totalQuestionsAnswered;
    private int correctCount;
    private double overallAccuracy;
    private String summary;
    private List<GapItem> gaps;
    private List<GapItem> strengths;
    private String recommendedNext;
}
