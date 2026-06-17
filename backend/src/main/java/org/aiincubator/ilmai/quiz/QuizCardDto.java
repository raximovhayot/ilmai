package org.aiincubator.ilmai.quiz;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

@Getter
@AllArgsConstructor
public final class QuizCardDto {

    private final UUID sessionId;
    private final UUID topicId;
    private final String difficulty;
    private final String locale;
    private final List<QuizCardQuestionDto> questions;
}
