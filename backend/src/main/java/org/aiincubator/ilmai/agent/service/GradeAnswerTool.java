package org.aiincubator.ilmai.agent.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aiincubator.ilmai.common.CurrentUser;
import org.aiincubator.ilmai.quiz.QuizApi;
import org.aiincubator.ilmai.quiz.QuizGradeDto;
import org.aiincubator.ilmai.quiz.QuizGradeException;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class GradeAnswerTool {

    private final QuizApi quizApi;

    @Tool(description = "Grade the current user's answer to ONE question of a quiz you previously started for them. "
            + "Identify the question by the quiz session id and its 1-based number. Returns graded=true with whether "
            + "the answer was correct, the correct answer and an explanation so you can give feedback, and the quiz "
            + "progress; or graded=false with a reason (session_not_found, question_not_found, already_answered) when "
            + "it could not be graded.")
    public GradeAnswerResult gradeAnswer(
            @ToolParam(description = "The id of the quiz session the question belongs to, as returned when the quiz "
                    + "was started.")
            String sessionId,
            @ToolParam(description = "The 1-based number of the question being answered (1 for the first question).")
            int questionNumber,
            @ToolParam(description = "The user's answer: the chosen option for a multiple-choice question, or their "
                    + "free text for short or open questions.")
            String answer,
            ToolContext toolContext) {
        CurrentUser currentUser = AgentToolContext.requireCurrentUser(toolContext);
        UUID parsedSessionId;
        try {
            parsedSessionId = UUID.fromString(sessionId == null ? "" : sessionId.trim());
        } catch (IllegalArgumentException ex) {
            return new GradeAnswerResult(false, null, "session_not_found");
        }
        try {
            QuizGradeDto result = quizApi.gradeAnswer(currentUser, parsedSessionId, questionNumber, answer);
            log.debug("agent.gradeAnswer user={} session={} question={} correct={}",
                    currentUser.getUserId(), parsedSessionId, questionNumber, result.getCorrect());
            return new GradeAnswerResult(true, result, null);
        } catch (QuizGradeException ex) {
            log.debug("agent.gradeAnswer unavailable user={} reason={}", currentUser.getUserId(), ex.getReason());
            return new GradeAnswerResult(false, null, ex.getReason().name().toLowerCase(Locale.ROOT));
        }
    }
}
