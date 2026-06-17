package org.aiincubator.ilmai.agent.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aiincubator.ilmai.common.i18n.MessageService;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.core.Ordered;

import java.util.List;

@RequiredArgsConstructor
@Slf4j
public class GroundingGuardAdvisor implements CallAdvisor {

    public static final String MESSAGE_KEY = "agent.grounding.empty";
    private static final int DEFAULT_ORDER = Ordered.HIGHEST_PRECEDENCE + 600;

    private final MessageService messageService;
    private final int order;

    public GroundingGuardAdvisor(MessageService messageService) {
        this(messageService, DEFAULT_ORDER);
    }

    @Override
    public int getOrder() {
        return order;
    }

    @Override
    public String getName() {
        return getClass().getSimpleName();
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        ChatClientResponse response = chain.nextCall(request);
        AgentRetrievalContext ctx = AgentRetrievalContext.current();
        if (ctx == null || ctx.hasGrounding()) {
            return response;
        }
        String fallback = messageService.get(MESSAGE_KEY);
        log.debug("grounding-guard: rewriting ungrounded response (retrieveCalls={})", ctx.callCount());
        ChatResponse rewritten = ChatResponse.builder()
                .generations(List.of(new Generation(new AssistantMessage(fallback))))
                .build();
        return ChatClientResponse.builder()
                .chatResponse(rewritten)
                .context(response.context())
                .build();
    }
}
