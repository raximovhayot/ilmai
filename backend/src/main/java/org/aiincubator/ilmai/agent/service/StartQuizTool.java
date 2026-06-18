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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class StartQuizTool {

    private static final String MODE_TIMED = "TIMED";
    private static final String MODE_EXPLAIN = "EXPLAIN";
    private static final String MODE_SINGLE = "SINGLE";
    private static final int SECONDS_PER_QUESTION = 30;

    private final QuizApi quizApi;

    @Tool(description = "Create a quiz for the current user from THEIR uploaded materials so they can practise or "
            + "self-test. Questions are authored only from the user's own content and are presented to the user as a "
            + "single self-contained, interactive quiz that they start, solve, and submit before chatting continues — "
            + "do NOT write the questions out yourself, just briefly introduce the quiz in one short sentence. "
            + "Choose a mode that suits the request: 'timed' (a countdown timer, all questions on one screen), "
            + "'explain' (one question at a time, the explanation is revealed right after each answer), or 'single' "
            + "(all questions on one screen, graded together after submission). "
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
            @ToolParam(required = false, description = "Optional quiz mode: 'timed', 'explain', or 'single'. "
                    + "Defaults to 'single' (all questions on one screen).")
            String mode,
            ToolContext toolContext) {
        CurrentUser currentUser = AgentToolContext.requireCurrentUser(toolContext);
        try {
            QuizCardDto card = quizApi.startQuiz(currentUser, scope, questionCount, difficulty);
            AgentQuizContext ctx = AgentQuizContext.current();
            if (ctx != null) {
                ctx.record(card);
            }
            String normalizedMode = normalizeMode(mode);
            int timeLimitSeconds = timeLimitFor(normalizedMode, card.getQuestions().size());
            emitQuiz(AgentToolContext.sink(toolContext), card, normalizedMode, timeLimitSeconds);
            log.debug("agent.startQuiz user={} session={} questions={} mode={}",
                    currentUser.getUserId(), card.getSessionId(), card.getQuestions().size(), normalizedMode);
            return new StartQuizResult(true, card.getSessionId(), card.getQuestions().size(), null);
        } catch (QuizUnavailableException ex) {
            log.debug("agent.startQuiz unavailable user={} reason={}", currentUser.getUserId(), ex.getReason());
            return new StartQuizResult(false, null, 0, ex.getReason().name().toLowerCase(Locale.ROOT));
        }
    }

    private void emitQuiz(SerializedPartSink sink, QuizCardDto card, String mode, int timeLimitSeconds) {
        if (sink == null) {
            return;
        }
        List<Map<String, Object>> questions = new ArrayList<>();
        for (QuizCardQuestionDto question : card.getQuestions()) {
            questions.add(Map.of(
                    "questionId", question.getQuestionId(),
                    "position", question.getPosition(),
                    "type", question.getType() == null ? "" : question.getType(),
                    "concept", question.getConcept() == null ? "" : question.getConcept(),
                    "prompt", question.getPrompt() == null ? "" : question.getPrompt(),
                    "options", question.getOptions() == null ? List.of() : question.getOptions(),
                    "materialId", question.getMaterialId() == null ? "" : question.getMaterialId().toString(),
                    "materialName", question.getMaterialName() == null ? "" : question.getMaterialName(),
                    "chunkIndex", question.getChunkIndex() == null ? -1 : question.getChunkIndex()
            ));
        }
        sink.data("quiz", Map.of(
                "sessionId", card.getSessionId(),
                "mode", mode,
                "timeLimitSeconds", timeLimitSeconds,
                "difficulty", card.getDifficulty() == null ? "" : card.getDifficulty(),
                "questions", questions
        ));
    }

    private String normalizeMode(String mode) {
        if (mode == null) {
            return MODE_SINGLE;
        }
        return switch (mode.trim().toLowerCase(Locale.ROOT)) {
            case "timed", "timer", "time" -> MODE_TIMED;
            case "explain", "explanation", "per_question", "stepwise", "step" -> MODE_EXPLAIN;
            default -> MODE_SINGLE;
        };
    }

    private int timeLimitFor(String mode, int questionCount) {
        if (!MODE_TIMED.equals(mode)) {
            return 0;
        }
        return Math.max(SECONDS_PER_QUESTION, questionCount * SECONDS_PER_QUESTION);
    }
}
