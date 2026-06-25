package org.aiincubator.ilmai.plan.payload;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.aiincubator.ilmai.plan.PlanActivity;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PlanStepResponse {

    private int dayIndex;
    private int orderInDay;
    private LocalDate scheduledDate;
    private String title;
    private PlanActivity activity;
    private List<PlanMaterialRef> materials;
    private String note;
    private String reflectionNote;
    private Integer quizScore;
    private boolean done;
    private OffsetDateTime completedAt;
    private boolean hasLesson;
    private OffsetDateTime lessonGeneratedAt;
}
