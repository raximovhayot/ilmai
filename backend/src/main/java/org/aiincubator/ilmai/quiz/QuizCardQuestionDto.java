package org.aiincubator.ilmai.quiz;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

@Getter
@AllArgsConstructor
public final class QuizCardQuestionDto {

    private final UUID questionId;
    private final int position;
    private final String type;
    private final String concept;
    private final String prompt;
    private final List<String> options;
    private final UUID materialId;
    private final String materialName;
    private final Integer chunkIndex;
}
