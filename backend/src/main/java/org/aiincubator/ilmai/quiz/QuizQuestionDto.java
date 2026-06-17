package org.aiincubator.ilmai.quiz;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@AllArgsConstructor
public final class QuizQuestionDto {

    private final UUID id;
    private final String concept;
    private final UUID materialId;
    private final Boolean isCorrect;
    private final OffsetDateTime updatedAt;
}
