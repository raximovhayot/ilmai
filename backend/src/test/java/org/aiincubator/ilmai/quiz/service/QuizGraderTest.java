package org.aiincubator.ilmai.quiz.service;

import org.aiincubator.ilmai.quiz.domain.QuestionType;
import org.aiincubator.ilmai.quiz.domain.QuizQuestion;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QuizGraderTest {

    private final QuizGrader grader = new QuizGrader(null);

    @Test
    void correctOptionIndex_matchesNormalizedOptionText() {
        QuizQuestion question = multipleChoice(List.of("Mercury", "Venus", "Earth"), "  earth ");

        assertThat(grader.correctOptionIndex(question)).isEqualTo(2);
    }

    @Test
    void correctOptionIndex_nullWhenNotMultipleChoice() {
        QuizQuestion question = new QuizQuestion();
        question.setType(QuestionType.OPEN_ENDED);
        question.setOptions(List.of("a", "b"));
        question.setCorrectAnswer("a");

        assertThat(grader.correctOptionIndex(question)).isNull();
    }

    @Test
    void correctOptionIndex_nullWhenNoOptionMatches() {
        QuizQuestion question = multipleChoice(List.of("A", "B"), "C");

        assertThat(grader.correctOptionIndex(question)).isNull();
    }

    private QuizQuestion multipleChoice(List<String> options, String correctAnswer) {
        QuizQuestion question = new QuizQuestion();
        question.setType(QuestionType.MULTIPLE_CHOICE);
        question.setOptions(options);
        question.setCorrectAnswer(correctAnswer);
        return question;
    }
}
