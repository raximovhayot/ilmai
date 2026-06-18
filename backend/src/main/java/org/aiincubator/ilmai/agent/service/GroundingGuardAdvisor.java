package org.aiincubator.ilmai.agent.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.core.Ordered;

@Slf4j
public class GroundingGuardAdvisor implements CallAdvisor {

    private static final int DEFAULT_ORDER = Ordered.HIGHEST_PRECEDENCE + 600;

    private final int order;

    public GroundingGuardAdvisor() {
        this(DEFAULT_ORDER);
    }

    public GroundingGuardAdvisor(int order) {
        this.order = order;
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
        if (ctx == null || ctx.callCount() == 0 || ctx.hasGrounding()) {
            return response;
        }
        AgentResponseFlags flags = AgentResponseFlags.current();
        if (flags != null) {
            flags.markLowConfidence();
        }
        log.debug("grounding-guard: retrieve returned no chunks; marking response low-confidence (retrieveCalls={})",
                ctx.callCount());
        return response;
    }
}
