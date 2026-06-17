package org.aiincubator.ilmai.agent.service;

import lombok.extern.slf4j.Slf4j;
import org.aiincubator.ilmai.agent.domain.ChatMemorySummary;
import org.aiincubator.ilmai.agent.domain.ChatMemorySummaryRepository;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.core.Ordered;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
public class ChatSummaryAdvisor implements CallAdvisor, StreamAdvisor {

    private static final int DEFAULT_ORDER = Ordered.HIGHEST_PRECEDENCE + 550;
    private static final int DEFAULT_MAX_CHARS = 1500;

    private static final String BLOCK_HEADER = "[earlier in this conversation]";

    private final ChatMemorySummaryRepository summaryRepository;
    private final int order;
    private final int maxChars;

    public ChatSummaryAdvisor(ChatMemorySummaryRepository summaryRepository) {
        this(summaryRepository, DEFAULT_ORDER, DEFAULT_MAX_CHARS);
    }

    public ChatSummaryAdvisor(ChatMemorySummaryRepository summaryRepository, int order, int maxChars) {
        this.summaryRepository = summaryRepository;
        this.order = order;
        this.maxChars = maxChars;
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
        return chain.nextCall(injectSummary(request));
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        return chain.nextStream(injectSummary(request));
    }

    private ChatClientRequest injectSummary(ChatClientRequest request) {
        UUID sessionId = resolveSessionId(request);
        if (sessionId == null) {
            return request;
        }
        String summary = summaryRepository.findBySessionId(sessionId)
                .map(ChatMemorySummary::getSummary)
                .orElse(null);
        if (summary == null || summary.isBlank()) {
            return request;
        }
        String block = buildBlock(summary);
        Prompt prompt = request.prompt();
        List<Message> messages = new ArrayList<>(prompt.getInstructions());
        boolean injected = false;
        for (int i = 0; i < messages.size(); i++) {
            if (messages.get(i) instanceof SystemMessage system) {
                messages.set(i, new SystemMessage(appendBlock(system.getText(), block)));
                injected = true;
                break;
            }
        }
        if (!injected) {
            messages.add(0, new SystemMessage(block));
        }
        return request.mutate().prompt(new Prompt(messages)).build();
    }

    private UUID resolveSessionId(ChatClientRequest request) {
        Object value = request.context().get(ChatMemory.CONVERSATION_ID);
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value.toString());
        } catch (IllegalArgumentException ex) {
            log.debug("chat-summary: non-UUID conversation id '{}'", value);
            return null;
        }
    }

    private String appendBlock(String systemText, String block) {
        if (systemText == null || systemText.isBlank()) {
            return block;
        }
        return systemText + "\n\n" + block;
    }

    private String buildBlock(String summary) {
        String trimmed = summary.trim();
        int budget = maxChars - BLOCK_HEADER.length() - 1;
        if (budget <= 0) {
            return BLOCK_HEADER;
        }
        if (trimmed.length() > budget) {
            trimmed = trimmed.substring(0, budget).trim();
        }
        return BLOCK_HEADER + "\n" + trimmed;
    }
}
