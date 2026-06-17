package org.aiincubator.ilmai.quiz.service;

import org.aiincubator.ilmai.quiz.QuizCardDto;
import org.aiincubator.ilmai.quiz.QuizCardQuestionDto;
import org.aiincubator.ilmai.quiz.payload.QuizQuestionResponse;
import org.aiincubator.ilmai.quiz.payload.QuizSessionResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface QuizCardMapper {

    @Mapping(target = "sessionId", source = "id")
    QuizCardDto toCard(QuizSessionResponse response);

    @Mapping(target = "questionId", source = "id")
    QuizCardQuestionDto toCardQuestion(QuizQuestionResponse question);
}
