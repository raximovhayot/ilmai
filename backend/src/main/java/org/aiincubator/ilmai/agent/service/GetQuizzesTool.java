package org.aiincubator.ilmai.agent.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aiincubator.ilmai.common.CurrentUser;
import org.aiincubator.ilmai.quiz.QuizApi;
import org.aiincubator.ilmai.quiz.QuizQuestionDto;
import org.aiincubator.ilmai.quiz.QuizSessionDto;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class GetQuizzesTool {

    private final QuizApi quizApi;

    @Tool(description = "List the current user's own quizzes, most recent first. Each entry has the quiz session id "
            + "(use it with getQuiz to inspect a single quiz), how many questions it has, how many the user has "
            + "answered and how many they got right. Call this when the user asks to see their quizzes or quiz "
            + "history. If hasQuizzes is false the user has not taken any quiz yet - offer to start one with "
            + "startQuiz.")
    public GetQuizzesResult getQuizzes(ToolContext toolContext) {
        CurrentUser currentUser = AgentToolContext.requireCurrentUser(toolContext);
        List<QuizSessionDto> sessions = quizApi.findAllSessionsForUser(currentUser.getUserId());
        List<QuizSummaryView> quizzes = new ArrayList<>();
        for (QuizSessionDto session : sessions) {
            List<QuizQuestionDto> questions = session.getQuestions() == null ? List.of() : session.getQuestions();
            int answered = 0;
            int correct = 0;
            for (QuizQuestionDto question : questions) {
                if (question.getIsCorrect() != null) {
                    answered++;
                    if (Boolean.TRUE.equals(question.getIsCorrect())) {
                        correct++;
                    }
                }
            }
            quizzes.add(new QuizSummaryView(session.getId(), questions.size(), answered, correct));
        }
        log.debug("agent.getQuizzes user={} quizzes={}", currentUser.getUserId(), quizzes.size());
        return new GetQuizzesResult(!quizzes.isEmpty(), quizzes);
    }
}
