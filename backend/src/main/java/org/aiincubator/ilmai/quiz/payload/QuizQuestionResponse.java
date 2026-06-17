package org.aiincubator.ilmai.quiz.payload;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class QuizQuestionResponse {

    private UUID id;
    private int position;
    private String type;
    private String concept;
    private String prompt;
    private List<String> options;
    private UUID materialId;
    private String materialName;
    private Integer chunkIndex;
    private String userAnswer;
    private Boolean isCorrect;
    private String correctAnswer;
    private String explanation;
    private String feedback;
}
