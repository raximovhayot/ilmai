package org.aiincubator.ilmai.agent.service;

import lombok.extern.slf4j.Slf4j;
import org.aiincubator.ilmai.common.i18n.MessageService;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.core.Ordered;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Slf4j
public class CitationGuardAdvisor implements CallAdvisor {

    public static final String REGENERATE_HINT_KEY = "agent.citation.regenerate_hint";
    private static final int DEFAULT_ORDER = Ordered.HIGHEST_PRECEDENCE + 700;
    private static final Pattern CITATION_PATTERN = Pattern.compile("\\[#[^\\]:]+:[^\\]]+]");

    private final MessageService messageService;
    private final int order;

    public CitationGuardAdvisor(MessageService messageService) {
        this(messageService, DEFAULT_ORDER);
    }

    public CitationGuardAdvisor(MessageService messageService, int order) {
        this.messageService = messageService;
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
        if (ctx == null || !ctx.hasGrounding()) {
            return response;
        }
        if (hasCitation(extractText(response))) {
            return response;
        }
        ChatClientRequest retryRequest = appendHint(request, messageService.get(REGENERATE_HINT_KEY));
        ChatClientResponse retry = chain.copy(this).nextCall(retryRequest);
        if (hasCitation(extractText(retry))) {
            return retry;
        }
        AgentResponseFlags flags = AgentResponseFlags.current();
        if (flags != null) {
            flags.markLowConfidence();
        }
        log.debug("citation-guard: marking response low-confidence after two failed attempts");
        return retry;
    }

    public static boolean containsCitation(String text) {
        return text != null && CITATION_PATTERN.matcher(text).find();
    }

    private boolean hasCitation(String text) {
        return containsCitation(text);
    }

    private String extractText(ChatClientResponse response) {
        if (response == null || response.chatResponse() == null
                || response.chatResponse().getResult() == null
                || response.chatResponse().getResult().getOutput() == null) {
            return null;
        }
        return response.chatResponse().getResult().getOutput().getText();
    }

    private ChatClientRequest appendHint(ChatClientRequest request, String hint) {
        Prompt prompt = request.prompt();
        List<Message> messages = new ArrayList<>(prompt.getInstructions());
        messages.add(new UserMessage(hint));
        return request.mutate().prompt(new Prompt(messages)).build();
    }
}
