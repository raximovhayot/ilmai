package org.aiincubator.ilmai.agent.api;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.aiincubator.ilmai.agent.ChatChannel;
import org.aiincubator.ilmai.agent.service.CoachStreamService;
import org.aiincubator.ilmai.common.CurrentUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import uz.uzinfoweb.uimessagestream.spring.UiMessageStreamHttp;

import java.util.UUID;

@RestController
@RequestMapping("/agent")
@RequiredArgsConstructor
public class AgentController {

    private final CoachStreamService coachStreamService;

    @PostMapping("/chat/{sessionId}")
    public SseEmitter chat(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable UUID sessionId,
            @Valid @RequestBody AgentChatRequest request,
            HttpServletResponse response) {
        UiMessageStreamHttp.applyHeaders(response);
        ChatChannel channel = request.getChannel() == null ? ChatChannel.WEB : request.getChannel();
        return coachStreamService.stream(currentUser, sessionId, request.getPrompt(), request.getContext(), channel);
    }
}
