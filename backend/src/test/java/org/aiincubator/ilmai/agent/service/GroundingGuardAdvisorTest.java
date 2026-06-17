package org.aiincubator.ilmai.agent.service;

import org.aiincubator.ilmai.agent.RetrievedChunk;
import org.aiincubator.ilmai.common.i18n.MessageService;
import org.aiincubator.ilmai.common.i18n.SupportedLocale;
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
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GroundingGuardAdvisorTest {

    private GroundingGuardAdvisor advisor;

    @BeforeEach
    void setUp() {
        ReloadableResourceBundleMessageSource source = new ReloadableResourceBundleMessageSource();
        source.setBasename("classpath:messages/messages");
        source.setDefaultEncoding(StandardCharsets.UTF_8.name());
        source.setFallbackToSystemLocale(false);
        source.setDefaultLocale(SupportedLocale.DEFAULT.getLocale());
        MessageService messageService = new MessageService(source);
        advisor = new GroundingGuardAdvisor(messageService);
    }

    @AfterEach
    void tearDown() {
        AgentRetrievalContext.clear();
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    void rewritesResponseWhenNoChunksWereRetrieved_inEnglish() {
        LocaleContextHolder.setLocale(Locale.ENGLISH);
        AgentRetrievalContext.begin();

        ChatClientResponse result = advisor.adviseCall(buildRequest("hello"), staticChain("from world knowledge"));

        assertThat(extractText(result)).isEqualTo(
                "I didn't find this in your uploads \u2014 want to upload something on it?");
    }

    @Test
    void rewritesResponseInRussian() {
        LocaleContextHolder.setLocale(new Locale("ru"));
        AgentRetrievalContext.begin();

        ChatClientResponse result = advisor.adviseCall(buildRequest("\u041f\u0440\u0438\u0432\u0435\u0442"), staticChain("answer"));

        assertThat(extractText(result)).startsWith("\u042f \u043d\u0435 \u043d\u0430\u0448\u0451\u043b");
    }

    @Test
    void rewritesResponseInUzbek() {
        LocaleContextHolder.setLocale(new Locale("uz"));
        AgentRetrievalContext.begin();

        ChatClientResponse result = advisor.adviseCall(buildRequest("salom"), staticChain("answer"));

        assertThat(extractText(result)).startsWith("Buni yuklamalaringizdan");
    }

    @Test
    void leavesResponseUntouchedWhenAtLeastOneChunkRetrieved() {
        LocaleContextHolder.setLocale(Locale.ENGLISH);
        AgentRetrievalContext ctx = AgentRetrievalContext.begin();
        ctx.recordCall(List.of(new RetrievedChunk(UUID.randomUUID(), "Notes", 1, "snippet", 0.9)));

        ChatClientResponse result = advisor.adviseCall(buildRequest("hello"), staticChain("grounded answer"));

        assertThat(extractText(result)).isEqualTo("grounded answer");
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
