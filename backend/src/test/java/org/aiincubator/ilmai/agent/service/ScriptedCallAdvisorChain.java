package org.aiincubator.ilmai.agent.service;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

final class ScriptedCallAdvisorChain implements CallAdvisorChain {

    private final List<String> texts;
    private final List<ChatClientRequest> requests = new ArrayList<>();
    private int index = 0;

    ScriptedCallAdvisorChain(List<String> texts) {
        this.texts = texts;
    }

    int calls() {
        return index;
    }

    List<ChatClientRequest> requests() {
        return requests;
    }

    @Override
    public ChatClientResponse nextCall(ChatClientRequest request) {
        requests.add(request);
        String text = texts.get(Math.min(index, texts.size() - 1));
        index++;
        ChatResponse cr = ChatResponse.builder()
                .generations(List.of(new Generation(new AssistantMessage(text))))
                .build();
        return ChatClientResponse.builder()
                .chatResponse(cr)
                .context(new HashMap<>())
                .build();
    }

    @Override
    public List<CallAdvisor> getCallAdvisors() {
        return List.of();
    }

    @Override
    public CallAdvisorChain copy(CallAdvisor advisor) {
        return this;
    }
}
