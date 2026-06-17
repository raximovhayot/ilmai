package org.aiincubator.ilmai.quiz.service;

import org.aiincubator.ilmai.quiz.QuizGradeDto;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface QuizGradeMapper {

    QuizGradeDto toDto(QuizGradeOutcome outcome);
}
