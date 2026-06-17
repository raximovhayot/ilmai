package org.aiincubator.ilmai.quiz;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

@Getter
@AllArgsConstructor
public final class QuizSessionDto {

    private final UUID id;
    private final UUID userId;
    private final List<QuizQuestionDto> questions;
}
