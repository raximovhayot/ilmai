package org.aiincubator.ilmai.quiz.service;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;
import org.aiincubator.ilmai.quiz.domain.QuestionType;

import java.util.List;
import java.util.UUID;

@Getter
@AllArgsConstructor
@ToString
public class QuestionDraft {

    private final QuestionType type;
    private final String concept;
    private final String prompt;
    private final List<String> options;
    private final String correctAnswer;
    private final String explanation;
    private final UUID materialId;
    private final String materialName;
    private final Integer chunkIndex;
}
