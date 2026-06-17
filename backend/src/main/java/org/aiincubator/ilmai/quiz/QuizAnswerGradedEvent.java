package org.aiincubator.ilmai.quiz;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class QuizAnswerGradedEvent {

    private final UUID userId;
    private final UUID sessionId;
    private final UUID questionId;
    private final UUID materialId;
    private final String concept;
    private final boolean correct;
    private final OffsetDateTime occurredAt;
}
