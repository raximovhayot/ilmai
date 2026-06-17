package org.aiincubator.ilmai.quiz.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.aiincubator.ilmai.common.CurrentUser;
import org.aiincubator.ilmai.common.payload.ApiResponse;
import org.aiincubator.ilmai.quiz.payload.AnswerQuestionRequest;
import org.aiincubator.ilmai.quiz.payload.QuizQuestionResponse;
import org.aiincubator.ilmai.quiz.payload.QuizSessionResponse;
import org.aiincubator.ilmai.quiz.payload.StartQuizRequest;
import org.aiincubator.ilmai.quiz.service.QuizService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/quiz/sessions")
@RequiredArgsConstructor
public class QuizController {

    private final QuizService quizService;

    @PostMapping
    public ApiResponse<QuizSessionResponse> start(@AuthenticationPrincipal CurrentUser currentUser,
                                                  @Valid @RequestBody(required = false) StartQuizRequest request) {
        return ApiResponse.ok(quizService.start(currentUser, request));
    }

    @GetMapping
    public ApiResponse<List<QuizSessionResponse>> list(@AuthenticationPrincipal CurrentUser currentUser) {
        return ApiResponse.ok(quizService.list(currentUser));
    }

    @GetMapping("/{sessionId}")
    public ApiResponse<QuizSessionResponse> get(@AuthenticationPrincipal CurrentUser currentUser,
                                                @PathVariable UUID sessionId) {
        return ApiResponse.ok(quizService.get(currentUser, sessionId));
    }

    @PostMapping("/{sessionId}/questions/{questionId}/answer")
    public ApiResponse<QuizQuestionResponse> answer(@AuthenticationPrincipal CurrentUser currentUser,
                                                    @PathVariable UUID sessionId,
                                                    @PathVariable UUID questionId,
                                                    @Valid @RequestBody AnswerQuestionRequest request) {
        return ApiResponse.ok(quizService.answer(currentUser, sessionId, questionId, request.getAnswer()));
    }

    @PostMapping("/{sessionId}/abandon")
    public ApiResponse<QuizSessionResponse> abandon(@AuthenticationPrincipal CurrentUser currentUser,
                                                    @PathVariable UUID sessionId) {
        return ApiResponse.ok(quizService.abandon(currentUser, sessionId));
    }
}
