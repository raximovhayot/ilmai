package org.aiincubator.ilmai.agent.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aiincubator.ilmai.common.CurrentUser;
import org.aiincubator.ilmai.quiz.QuizApi;
import org.aiincubator.ilmai.quiz.QuizCardDto;
import org.aiincubator.ilmai.quiz.QuizCardQuestionDto;
import org.aiincubator.ilmai.quiz.QuizUnavailableException;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import uz.uzinfoweb.uimessagestream.spring.SerializedPartSink;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class StartQuizTool {

    private final QuizApi quizApi;

    @Tool(description = "Create a quiz for the current user from THEIR uploaded materials so they can practise or "
            + "self-test. Questions are authored only from the user's own content and shown to the user as an "
            + "interactive quiz card — do NOT write the questions out yourself, just briefly introduce the quiz. "
            + "Returns created=true with the question count, or created=false with a reason "
            + "(materials_missing, quota_exceeded, no_questions) when a quiz could not be built.")
    public StartQuizResult startQuiz(
            @ToolParam(required = false, description = "Optional topic or scope in the user's words, e.g. "
                    + "'photosynthesis'. Omit to quiz across the user's materials broadly.")
            String scope,
            @ToolParam(required = false, description = "Optional number of questions (1-20). Defaults to 5.")
            Integer questionCount,
            @ToolParam(required = false, description = "Optional difficulty: easy, solid, or hard. Defaults to solid.")
            String difficulty,
            ToolContext toolContext) {
        CurrentUser currentUser = AgentToolContext.requireCurrentUser(toolContext);
        try {
            QuizCardDto card = quizApi.startQuiz(currentUser, scope, questionCount, difficulty);
            AgentQuizContext ctx = AgentQuizContext.current();
            if (ctx != null) {
                ctx.record(card);
            }
            emitQuizCards(AgentToolContext.sink(toolContext), card);
            log.debug("agent.startQuiz user={} session={} questions={}",
                    currentUser.getUserId(), card.getSessionId(), card.getQuestions().size());
            return new StartQuizResult(true, card.getSessionId(), card.getQuestions().size(), null);
        } catch (QuizUnavailableException ex) {
            log.debug("agent.startQuiz unavailable user={} reason={}", currentUser.getUserId(), ex.getReason());
            return new StartQuizResult(false, null, 0, ex.getReason().name().toLowerCase(Locale.ROOT));
        }
    }

    private void emitQuizCards(SerializedPartSink sink, QuizCardDto card) {
        if (sink == null) {
            return;
        }
        for (QuizCardQuestionDto question : card.getQuestions()) {
            sink.data("quiz", Map.of(
                    "sessionId", card.getSessionId(),
                    "questionId", question.getQuestionId(),
                    "position", question.getPosition(),
                    "type", question.getType(),
                    "concept", question.getConcept() == null ? "" : question.getConcept(),
                    "prompt", question.getPrompt() == null ? "" : question.getPrompt(),
                    "options", question.getOptions() == null ? List.of() : question.getOptions(),
                    "materialId", question.getMaterialId() == null ? "" : question.getMaterialId().toString(),
                    "materialName", question.getMaterialName() == null ? "" : question.getMaterialName(),
                    "chunkIndex", question.getChunkIndex() == null ? -1 : question.getChunkIndex()
            ));
        }
    }
}
