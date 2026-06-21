package org.aiincubator.ilmai.agent.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.aiincubator.ilmai.agent.service.ChatSessionService;
import org.aiincubator.ilmai.agent.service.ChatTranscriptService;
import org.aiincubator.ilmai.common.CurrentUser;
import org.aiincubator.ilmai.common.payload.ApiResponse;
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
@RequestMapping("/agent/sessions")
@RequiredArgsConstructor
public class ChatSessionController {

    private final ChatSessionService chatSessionService;
    private final ChatTranscriptService chatTranscriptService;

    @PostMapping
    public ApiResponse<ChatSessionResponse> create(
            @AuthenticationPrincipal CurrentUser currentUser,
            @Valid @RequestBody(required = false) CreateChatSessionRequest request) {
        return ApiResponse.ok(chatSessionService.create(currentUser, request));
    }

    @GetMapping
    public ApiResponse<List<ChatSessionResponse>> list(@AuthenticationPrincipal CurrentUser currentUser) {
        return ApiResponse.ok(chatSessionService.getAll(currentUser));
    }

    @GetMapping("/{sessionId}/messages")
    public ApiResponse<List<ChatMessageResponse>> messages(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable UUID sessionId) {
        return ApiResponse.ok(chatTranscriptService.getMessages(currentUser, sessionId));
    }
}
