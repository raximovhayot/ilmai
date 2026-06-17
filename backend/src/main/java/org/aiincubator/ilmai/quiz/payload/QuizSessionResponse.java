package org.aiincubator.ilmai.quiz.payload;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class QuizSessionResponse {

    private UUID id;
    private UUID topicId;
    private String difficulty;
    private String locale;
    private String status;
    private Double score;
    private int correctCount;
    private int totalCount;
    private OffsetDateTime startedAt;
    private OffsetDateTime completedAt;
    private List<QuizQuestionResponse> questions;
}
