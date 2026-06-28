package org.aiincubator.ilmai.quiz.payload;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StartQuizRequest {

    private UUID topicId;

    private UUID roomId;

    @Size(max = 20)
    private String difficulty;

    @Size(max = 10)
    private String locale;

    @Min(1)
    @Max(20)
    private Integer questionCount;
}
