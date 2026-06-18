package org.aiincubator.ilmai.agent.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aiincubator.ilmai.common.CurrentUser;
import org.aiincubator.ilmai.quiz.QuizApi;
import org.aiincubator.ilmai.quiz.QuizQuestionDto;
import org.aiincubator.ilmai.quiz.QuizSessionDto;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class GetQuizTool {

    private final QuizApi quizApi;

    @Tool(description = "Return ONE of the current user's quizzes by its session id, including each question's "
            + "concept, the material it came from and whether the user answered it correctly, plus the overall "
            + "answered and correct counts. Call this when the user asks about a specific quiz. If found is false the "
            + "quiz does not exist or does not belong to the user.")
    public GetQuizResult getQuiz(
            @ToolParam(description = "The id of the quiz session to inspect, as listed by getQuizzes or returned when "
                    + "the quiz was started.")
            String sessionId,
            ToolContext toolContext) {
        CurrentUser currentUser = AgentToolContext.requireCurrentUser(toolContext);
        UUID parsedSessionId;
        try {
            parsedSessionId = UUID.fromString(sessionId == null ? "" : sessionId.trim());
        } catch (IllegalArgumentException ex) {
            return notFound();
        }
        QuizSessionDto session = quizApi.findSessionForUser(currentUser, parsedSessionId).orElse(null);
        if (session == null) {
            return notFound();
        }
        List<QuizQuestionDto> questions = session.getQuestions() == null ? List.of() : session.getQuestions();
        List<QuizQuestionView> views = new ArrayList<>();
        int answered = 0;
        int correct = 0;
        int position = 1;
        for (QuizQuestionDto question : questions) {
            if (question.getIsCorrect() != null) {
                answered++;
                if (Boolean.TRUE.equals(question.getIsCorrect())) {
                    correct++;
                }
            }
            views.add(new QuizQuestionView(question.getId(), position++, question.getConcept(),
                    question.getMaterialId(), question.getIsCorrect()));
        }
        log.debug("agent.getQuiz user={} session={} questions={}",
                currentUser.getUserId(), parsedSessionId, views.size());
        return new GetQuizResult(true, session.getId(), views.size(), answered, correct, views);
    }

    private GetQuizResult notFound() {
        return new GetQuizResult(false, null, 0, 0, 0, List.of());
    }
}
