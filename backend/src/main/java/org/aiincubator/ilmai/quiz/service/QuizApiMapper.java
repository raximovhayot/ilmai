package org.aiincubator.ilmai.quiz.service;

import org.aiincubator.ilmai.quiz.QuizQuestionDto;
import org.aiincubator.ilmai.quiz.QuizSessionDto;
import org.aiincubator.ilmai.quiz.domain.QuizQuestion;
import org.aiincubator.ilmai.quiz.domain.QuizSession;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface QuizApiMapper {

    QuizQuestionDto toDto(QuizQuestion question);

    List<QuizQuestionDto> toQuestionDtoList(List<QuizQuestion> questions);

    QuizSessionDto toDto(QuizSession session);

    List<QuizSessionDto> toSessionDtoList(List<QuizSession> sessions);
}
