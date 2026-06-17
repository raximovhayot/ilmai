package org.aiincubator.ilmai.agent.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.aiincubator.ilmai.agent.service.ChatSessionService;
import org.aiincubator.ilmai.common.CurrentUser;
import org.aiincubator.ilmai.common.payload.ApiResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/agent/sessions")
@RequiredArgsConstructor
public class ChatSessionController {

    private final ChatSessionService chatSessionService;

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
}
