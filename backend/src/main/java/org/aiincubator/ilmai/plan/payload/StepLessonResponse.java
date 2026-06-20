package org.aiincubator.ilmai.plan.payload;

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
public class StepLessonResponse {

    private int dayIndex;
    private String title;
    private String content;
    private List<LessonCitationResponse> citations;
    private OffsetDateTime generatedAt;
}
