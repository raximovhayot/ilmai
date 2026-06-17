package org.aiincubator.ilmai.quiz.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aiincubator.ilmai.ai.IlmaiChatClientFactory;
import org.aiincubator.ilmai.quiz.domain.QuestionType;
import org.aiincubator.ilmai.quiz.domain.QuizQuestion;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

@Component
@RequiredArgsConstructor
@Slf4j
public class QuizGrader {

    private final IlmaiChatClientFactory chatClientFactory;

    public QuizGradeResult grade(QuizQuestion question, String userAnswer) {
        String trimmed = userAnswer == null ? "" : userAnswer.trim();
        if (question.getType() == QuestionType.OPEN_ENDED) {
            return gradeOpen(question, trimmed);
        }
        boolean correct = normalize(trimmed).equals(normalize(question.getCorrectAnswer()));
        String feedback = correct
                ? defaultPositiveFeedback(question)
                : defaultNegativeFeedback(question);
        return new QuizGradeResult(correct, feedback);
    }

    public Integer correctOptionIndex(QuizQuestion question) {
        if (question.getType() != QuestionType.MULTIPLE_CHOICE) {
            return null;
        }
        List<String> options = question.getOptions();
        if (options == null || options.size() < 2) {
            return null;
        }
        String target = normalize(question.getCorrectAnswer());
        if (target.isEmpty()) {
            return null;
        }
        for (int i = 0; i < options.size(); i++) {
            if (normalize(options.get(i)).equals(target)) {
                return i;
            }
        }
        return null;
    }

    private QuizGradeResult gradeOpen(QuizQuestion question, String trimmed) {
        if (trimmed.isEmpty()) {
            return new QuizGradeResult(false, "An empty answer cannot be evaluated.");
        }
        ChatClient.Builder builder = chatClientFactory.builder();
        if (builder == null) {
            return new QuizGradeResult(null, "Manual review required — AI grader unavailable.");
        }
        String prompt = """
                You are a strict but kind tutor grading an open-ended answer.
                Question: %s
                Reference answer: %s
                User answer: %s
                Return exactly two lines:
                Line 1: CORRECT or INCORRECT
                Line 2: one short sentence of feedback in the user's language."""
                .formatted(question.getPrompt(),
                        question.getCorrectAnswer() == null ? "" : question.getCorrectAnswer(),
                        trimmed);
        try {
            String response = builder.build().prompt(new Prompt(prompt)).call().content();
            return parseGraderReply(response);
        } catch (RuntimeException ex) {
            log.warn("open-ended grading failed: {}", ex.toString());
            return new QuizGradeResult(null, "Manual review required — AI grader temporarily unavailable.");
        }
    }

    private QuizGradeResult parseGraderReply(String response) {
        if (response == null) {
            return new QuizGradeResult(null, "");
        }
        String[] lines = response.strip().split("\n", 2);
        String verdict = lines[0].trim().toUpperCase(Locale.ROOT);
        Boolean correct = verdict.startsWith("CORRECT") ? Boolean.TRUE
                : verdict.startsWith("INCORRECT") ? Boolean.FALSE
                : null;
        String feedback = lines.length > 1 ? lines[1].trim() : "";
        return new QuizGradeResult(correct, feedback);
    }

    private String defaultPositiveFeedback(QuizQuestion q) {
        String exp = q.getExplanation();
        return (exp == null || exp.isBlank())
                ? "Correct — well done."
                : "Correct — " + exp;
    }

    private String defaultNegativeFeedback(QuizQuestion q) {
        String exp = q.getExplanation();
        return (exp == null || exp.isBlank())
                ? "Not quite. Review the cited chunk and try again later."
                : "Not quite. " + exp;
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        String n = Normalizer.normalize(value.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFKC);
        return n.replaceAll("\\s+", " ");
    }
}
