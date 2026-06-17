package org.aiincubator.ilmai.agent.service;

import org.aiincubator.ilmai.agent.domain.ChatMemorySummary;
import org.aiincubator.ilmai.agent.domain.ChatMemorySummaryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ChatSummaryAdvisorTest {

    private final UUID sessionId = UUID.randomUUID();

    @Test
    void injectsSummaryIntoSystemMessage() {
        ChatMemorySummaryRepository repository = mock(ChatMemorySummaryRepository.class);
        when(repository.findBySessionId(sessionId)).thenReturn(Optional.of(summary("Learner is preparing for IELTS.")));
        ChatSummaryAdvisor advisor = new ChatSummaryAdvisor(repository);

        ScriptedCallAdvisorChain chain = new ScriptedCallAdvisorChain(List.of("ok"));
        advisor.adviseCall(request("You are the Coach.", sessionId.toString()), chain);

        String system = systemText(chain.requests().get(0));
        assertThat(system).contains("You are the Coach.")
                .contains("[earlier in this conversation]")
                .contains("Learner is preparing for IELTS.");
    }

    @Test
    void passesThroughWhenNoSummary() {
        ChatMemorySummaryRepository repository = mock(ChatMemorySummaryRepository.class);
        when(repository.findBySessionId(sessionId)).thenReturn(Optional.empty());
        ChatSummaryAdvisor advisor = new ChatSummaryAdvisor(repository);

        ScriptedCallAdvisorChain chain = new ScriptedCallAdvisorChain(List.of("ok"));
        advisor.adviseCall(request("base only", sessionId.toString()), chain);

        assertThat(systemText(chain.requests().get(0))).isEqualTo("base only");
    }

    @Test
    void passesThroughWhenNoConversationId() {
        ChatMemorySummaryRepository repository = mock(ChatMemorySummaryRepository.class);
        ChatSummaryAdvisor advisor = new ChatSummaryAdvisor(repository);

        ScriptedCallAdvisorChain chain = new ScriptedCallAdvisorChain(List.of("ok"));
        advisor.adviseCall(request("base only", null), chain);

        assertThat(systemText(chain.requests().get(0))).isEqualTo("base only");
        verifyNoInteractions(repository);
    }

    @Test
    void passesThroughWhenConversationIdNotUuid() {
        ChatMemorySummaryRepository repository = mock(ChatMemorySummaryRepository.class);
        ChatSummaryAdvisor advisor = new ChatSummaryAdvisor(repository);

        ScriptedCallAdvisorChain chain = new ScriptedCallAdvisorChain(List.of("ok"));
        advisor.adviseCall(request("base only", "not-a-uuid"), chain);

        assertThat(systemText(chain.requests().get(0))).isEqualTo("base only");
        verifyNoInteractions(repository);
    }

    @Test
    void capsBlockAtMaxChars() {
        ChatMemorySummaryRepository repository = mock(ChatMemorySummaryRepository.class);
        when(repository.findBySessionId(any())).thenReturn(Optional.of(summary("X".repeat(500))));
        ChatSummaryAdvisor advisor = new ChatSummaryAdvisor(repository, 0, 60);

        ScriptedCallAdvisorChain chain = new ScriptedCallAdvisorChain(List.of("ok"));
        advisor.adviseCall(request("", sessionId.toString()), chain);

        String system = systemText(chain.requests().get(0));
        assertThat(system).contains("[earlier in this conversation]");
        assertThat(system.length()).isLessThanOrEqualTo(60);
    }

    private ChatMemorySummary summary(String text) {
        ChatMemorySummary entity = new ChatMemorySummary();
        entity.setSessionId(sessionId);
        entity.setSummary(text);
        return entity;
    }

    private ChatClientRequest request(String systemText, String conversationId) {
        Map<String, Object> context = new HashMap<>();
        if (conversationId != null) {
            context.put(ChatMemory.CONVERSATION_ID, conversationId);
        }
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemText));
        messages.add(new UserMessage("hello"));
        return ChatClientRequest.builder()
                .prompt(new Prompt(messages))
                .context(context)
                .build();
    }

    private String systemText(ChatClientRequest request) {
        return request.prompt().getInstructions().stream()
                .filter(message -> message instanceof SystemMessage)
                .map(Message::getText)
                .findFirst()
                .orElse("");
    }
}
