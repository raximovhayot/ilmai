package org.aiincubator.ilmai.agent.service;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor
public final class QuizQuestionView {

    private final UUID questionId;
    private final int position;
    private final String concept;
    private final UUID materialId;
    private final Boolean correct;
}
