package org.aiincubator.ilmai.agent.service;

import org.aiincubator.ilmai.agent.RetrievedChunk;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GroundingGuardAdvisorTest {

    private GroundingGuardAdvisor advisor;

    @BeforeEach
    void setUp() {
        advisor = new GroundingGuardAdvisor();
    }

    @AfterEach
    void tearDown() {
        AgentRetrievalContext.clear();
        AgentResponseFlags.clear();
    }

    @Test
    void marksLowConfidenceWhenRetrieveCalledButNoChunksRetrieved() {
        AgentRetrievalContext ctx = AgentRetrievalContext.begin();
        ctx.recordCall(List.of());
        AgentResponseFlags flags = AgentResponseFlags.begin();

        ChatClientResponse result = advisor.adviseCall(buildRequest("hello"), staticChain("from world knowledge"));

        assertThat(extractText(result)).isEqualTo("from world knowledge");
        assertThat(flags.isLowConfidence()).isTrue();
    }

    @Test
    void leavesResponseUntouchedWhenRetrieveWasNeverCalled() {
        AgentRetrievalContext.begin();
        AgentResponseFlags flags = AgentResponseFlags.begin();

        ChatClientResponse result = advisor.adviseCall(buildRequest("hello"), staticChain("general answer"));

        assertThat(extractText(result)).isEqualTo("general answer");
        assertThat(flags.isLowConfidence()).isFalse();
    }

    @Test
    void leavesResponseUntouchedWhenAtLeastOneChunkRetrieved() {
        AgentRetrievalContext ctx = AgentRetrievalContext.begin();
        ctx.recordCall(List.of(new RetrievedChunk(UUID.randomUUID(), "Notes", 1, "snippet", 0.9)));
        AgentResponseFlags flags = AgentResponseFlags.begin();

        ChatClientResponse result = advisor.adviseCall(buildRequest("hello"), staticChain("grounded answer"));

        assertThat(extractText(result)).isEqualTo("grounded answer");
        assertThat(flags.isLowConfidence()).isFalse();
    }

    @Test
    void doesNothingIfNoAgentRetrievalContextIsActive() {
        ChatClientResponse result = advisor.adviseCall(buildRequest("hello"), staticChain("free-form answer"));

        assertThat(extractText(result)).isEqualTo("free-form answer");
    }

    private ChatClientRequest buildRequest(String userText) {
        return ChatClientRequest.builder()
                .prompt(new Prompt(List.of(new UserMessage(userText))))
                .context(new HashMap<>())
                .build();
    }

    private CallAdvisorChain staticChain(String assistantText) {
        ChatResponse chatResponse = ChatResponse.builder()
                .generations(List.of(new Generation(new AssistantMessage(assistantText))))
                .build();
        ChatClientResponse response = ChatClientResponse.builder()
                .chatResponse(chatResponse)
                .context(new HashMap<>())
                .build();
        return new CallAdvisorChain() {
            @Override
            public ChatClientResponse nextCall(ChatClientRequest request) {
                return response;
            }

            @Override
            public java.util.List<org.springframework.ai.chat.client.advisor.api.CallAdvisor> getCallAdvisors() {
                return List.of();
            }

            @Override
            public CallAdvisorChain copy(org.springframework.ai.chat.client.advisor.api.CallAdvisor advisor) {
                return this;
            }
        };
    }

    private String extractText(ChatClientResponse response) {
        ChatResponse cr = response.chatResponse();
        return cr.getResult().getOutput().getText();
    }
}
