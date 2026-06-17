package org.aiincubator.ilmai.quiz.service;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class QuizPollSpec {

    private final int correctOptionId;
    private final String explanation;
}
