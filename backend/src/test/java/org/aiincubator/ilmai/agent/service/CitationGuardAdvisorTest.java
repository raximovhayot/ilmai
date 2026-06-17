package org.aiincubator.ilmai.agent.service;

import org.aiincubator.ilmai.agent.RetrievedChunk;
import org.aiincubator.ilmai.common.i18n.MessageService;
import org.aiincubator.ilmai.common.i18n.SupportedLocale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CitationGuardAdvisorTest {

    private CitationGuardAdvisor advisor;

    @BeforeEach
    void setUp() {
        ReloadableResourceBundleMessageSource source = new ReloadableResourceBundleMessageSource();
        source.setBasename("classpath:messages/messages");
        source.setDefaultEncoding(StandardCharsets.UTF_8.name());
        source.setFallbackToSystemLocale(false);
        source.setDefaultLocale(SupportedLocale.DEFAULT.getLocale());
        MessageService messageService = new MessageService(source);
        advisor = new CitationGuardAdvisor(messageService);
        LocaleContextHolder.setLocale(Locale.ENGLISH);
    }

    @AfterEach
    void tearDown() {
        AgentRetrievalContext.clear();
        AgentResponseFlags.clear();
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    void passesThroughWhenNoRetrievalContext() {
        ChatClientResponse result = advisor.adviseCall(buildRequest("hello"),
                new ScriptedCallAdvisorChain(List.of("free-form answer")));

        assertThat(extractText(result)).isEqualTo("free-form answer");
        assertThat(AgentResponseFlags.current()).isNull();
    }

    @Test
    void passesThroughWhenRetrievalEmpty() {
        AgentRetrievalContext.begin();
        ChatClientResponse result = advisor.adviseCall(buildRequest("hello"),
                new ScriptedCallAdvisorChain(List.of("answer without citation")));

        assertThat(extractText(result)).isEqualTo("answer without citation");
    }

    @Test
    void passesThroughWhenAnswerAlreadyHasCitation() {
        seedRetrieval();
        ScriptedCallAdvisorChain chain = new ScriptedCallAdvisorChain(List.of(
                "The textbook defines photosynthesis [#abc:p12] as ..."));

        ChatClientResponse result = advisor.adviseCall(buildRequest("define photosynthesis"), chain);

        assertThat(extractText(result)).contains("[#abc:p12]");
        assertThat(chain.calls()).isEqualTo(1);
    }

    @Test
    void regeneratesOnceWhenCitationMissing_thenAcceptsSecondAttempt() {
        AgentResponseFlags.begin();
        seedRetrieval();
        ScriptedCallAdvisorChain chain = new ScriptedCallAdvisorChain(List.of(
                "First attempt without any tag.",
                "Second attempt with citation [#abc:t1]."));

        ChatClientResponse result = advisor.adviseCall(buildRequest("ask"), chain);

        assertThat(extractText(result)).contains("[#abc:t1]");
        assertThat(chain.calls()).isEqualTo(2);
        assertThat(AgentResponseFlags.current().isLowConfidence()).isFalse();
    }

    @Test
    void marksLowConfidenceWhenBothAttemptsLackCitation() {
        AgentResponseFlags.begin();
        seedRetrieval();
        ScriptedCallAdvisorChain chain = new ScriptedCallAdvisorChain(List.of(
                "first ungrounded text",
                "second ungrounded text"));

        ChatClientResponse result = advisor.adviseCall(buildRequest("ask"), chain);

        assertThat(extractText(result)).isEqualTo("second ungrounded text");
        assertThat(chain.calls()).isEqualTo(2);
        assertThat(AgentResponseFlags.current().isLowConfidence()).isTrue();
    }

    @Test
    void tolerantWhenAgentResponseFlagsAreAbsent() {
        seedRetrieval();
        ScriptedCallAdvisorChain chain = new ScriptedCallAdvisorChain(List.of("no cite", "still no cite"));

        ChatClientResponse result = advisor.adviseCall(buildRequest("ask"), chain);

        assertThat(extractText(result)).isEqualTo("still no cite");
        assertThat(chain.calls()).isEqualTo(2);
    }

    private void seedRetrieval() {
        AgentRetrievalContext ctx = AgentRetrievalContext.begin();
        ctx.recordCall(List.of(new RetrievedChunk(UUID.randomUUID(), "Notes", 1, "snippet", 0.9)));
    }

    private ChatClientRequest buildRequest(String userText) {
        return ChatClientRequest.builder()
                .prompt(new Prompt(List.of(new UserMessage(userText))))
                .context(new HashMap<>())
                .build();
    }

    private String extractText(ChatClientResponse response) {
        return response.chatResponse().getResult().getOutput().getText();
    }
}
