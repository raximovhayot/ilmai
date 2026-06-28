package org.aiincubator.ilmai.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.aiincubator.ilmai.agent.api.AgentController;
import org.aiincubator.ilmai.ai.IlmaiChatClientFactory;
import org.aiincubator.ilmai.ai.RetrievalApi;
import org.aiincubator.ilmai.ai.RetrievedChunkDto;
import org.aiincubator.ilmai.common.CurrentUser;
import org.aiincubator.ilmai.common.i18n.MessageService;
import org.aiincubator.ilmai.common.quota.IlmTokenReservation;
import org.aiincubator.ilmai.common.quota.QuotaService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.ToolCallAdvisor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.execution.DefaultToolExecutionExceptionProcessor;
import org.springframework.ai.tool.resolution.StaticToolCallbackResolver;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import reactor.core.publisher.Flux;
import uz.uzinfoweb.uimessagestream.spring.ApprovalPolicy;
import uz.uzinfoweb.uimessagestream.spring.ErrorMessageResolver;
import uz.uzinfoweb.uimessagestream.spring.RecordingToolCallingManager;
import uz.uzinfoweb.uimessagestream.spring.UiMessageStreamEmitter;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CoachStreamServiceIntegrationTest {

    private final UUID userId = UUID.randomUUID();
    private final UUID sessionId = UUID.randomUUID();
    private final UUID materialId = UUID.randomUUID();
    private final CurrentUser currentUser = new CurrentUser(userId);

    private final QuotaService quotaService = mock(QuotaService.class);
    private final IlmTokenReservation reservation = mock(IlmTokenReservation.class);
    private final ChatSessionService chatSessionService = mock(ChatSessionService.class);
    private final ChatMemorySummarizer chatMemorySummarizer = mock(ChatMemorySummarizer.class);
    private final UserFactExtractor userFactExtractor = mock(UserFactExtractor.class);
    private final MessageService messageService = mock(MessageService.class);

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void streamsNativeToolAndDataFramesInOrderOverSse() throws Exception {
        RetrievalApi retrievalApi = mock(RetrievalApi.class);
        when(retrievalApi.retrieve(eq(userId), anyString())).thenReturn(List.of(
                new RetrievedChunkDto(materialId, "Bio Notes", 2, "Photosynthesis is how plants eat light.", 0.95)));
        RetrieveTool retrieveTool = new RetrieveTool(retrievalApi);

        String body = perform(toolCallingClient(retrieveTool), "explain photosynthesis from my notes");

        int start = body.indexOf("\"type\":\"start\"");
        int toolInput = body.indexOf("\"type\":\"tool-input-available\"");
        int citation = body.indexOf("\"type\":\"data-citation\"");
        int toolOutput = body.indexOf("\"type\":\"tool-output-available\"");
        int firstDelta = body.indexOf("\"type\":\"text-delta\"");
        int finish = body.indexOf("\"type\":\"finish\"");
        int done = body.indexOf("[DONE]");

        assertThat(start).isNotNegative();
        assertThat(toolInput).isGreaterThan(start);
        assertThat(citation).isGreaterThan(toolInput);
        assertThat(toolOutput).isGreaterThan(citation);
        assertThat(firstDelta).isGreaterThan(toolOutput);
        assertThat(finish).isGreaterThan(firstDelta);
        assertThat(done).isGreaterThan(finish);

        assertThat(body).contains("\"toolName\":\"retrieve\"");
        assertThat(body).contains("\"dynamic\":true");
        assertThat(body).contains("\"locator\":\"t2\"");
        assertThat(body).contains("\"delta\":\"Photosynthesis converts \"");
        assertThat(body).contains("\"delta\":\"light into energy \"");
        assertThat(body).doesNotContain("\"type\":\"data-confidence\"");

        verify(quotaService).commit(eq(reservation), anyInt());
        verify(quotaService, never()).refund(any());
        verify(chatMemorySummarizer).maintain(any(CurrentUser.class), eq(sessionId));
        verify(userFactExtractor).extract(any(CurrentUser.class), eq(sessionId));
    }

    @Test
    void flagsUngroundedTurnWithTrailingLowConfidence() throws Exception {
        String body = perform(plainTextClient("Just chatting, no sources here. "), "hello");

        int lastDelta = body.lastIndexOf("\"type\":\"text-delta\"");
        int confidence = body.indexOf("\"type\":\"data-confidence\"");
        int finish = body.indexOf("\"type\":\"finish\"");

        assertThat(confidence).isGreaterThan(lastDelta);
        assertThat(finish).isGreaterThan(confidence);
        assertThat(body).contains("\"level\":\"low\"");

        verify(quotaService).commit(eq(reservation), anyInt());
    }

    @Test
    void topicScopedTurnIsNotFlaggedLowConfidenceWhenUngrounded() throws Exception {
        String body = performWithContext(plainTextClient("Studying the topic, no sources. "),
                "what is this about", "Lesson title: Photosynthesis\n\nLesson content: Plants use light.");

        assertThat(body).doesNotContain("\"type\":\"data-confidence\"");
        assertThat(body).doesNotContain("\"level\":\"low\"");
        assertThat(body).contains("[DONE]");

        verify(quotaService).commit(eq(reservation), anyInt());
    }

    @Test
    void quotaExceededYieldsLocalizedErrorFrameWithoutModelCall() throws Exception {
        when(messageService.get(CoachTurnSupport.QUOTA_EXCEEDED_MESSAGE_KEY))
                .thenReturn("bugungi limit tugadi");
        when(quotaService.canSpend(userId, CoachTurnSupport.PER_TURN_ESTIMATE_ILM_TOKENS)).thenReturn(false);

        ChatClient client = mock(ChatClient.class);
        String body = perform(client, "hello", false);

        int start = body.indexOf("\"type\":\"start\"");
        int error = body.indexOf("\"type\":\"error\"");
        int finish = body.indexOf("\"type\":\"finish\"");

        assertThat(start).isNotNegative();
        assertThat(error).isGreaterThan(start);
        assertThat(finish).isGreaterThan(error);
        assertThat(body).contains("\"errorText\":\"bugungi limit tugadi\"");
        assertThat(body).contains("[DONE]");

        verify(quotaService, never()).reserve(any(UUID.class), anyInt());
    }

    @Test
    void masksModelFailureOnTheWireAndRefundsReservation() throws Exception {
        ChatModel failingModel = new ScriptedChatModel(null) {
            @Override
            public Flux<ChatResponse> stream(Prompt prompt) {
                return Flux.concat(
                        Flux.just(delta("partial ")),
                        Flux.error(new RuntimeException("jdbc password is hunter2")));
            }
        };
        ChatClient client = ChatClient.builder(failingModel).build();

        String body = perform(client, "hello");

        assertThat(body).contains("\"type\":\"error\"");
        assertThat(body).doesNotContain("hunter2");
        assertThat(body).contains("[DONE]");

        verify(quotaService).refund(reservation);
        verify(quotaService, never()).commit(any(), anyInt());
    }

    private ChatClient toolCallingClient(RetrieveTool retrieveTool) {
        ToolCallingManager delegate = DefaultToolCallingManager.builder()
                .toolCallbackResolver(new StaticToolCallbackResolver(List.of(ToolCallbacks.from(retrieveTool))))
                .toolExecutionExceptionProcessor(new DefaultToolExecutionExceptionProcessor(true))
                .build();
        ToolCallingManager recording = new RecordingToolCallingManager(
                delegate, new ObjectMapper(), true, ApprovalPolicy.NONE, ErrorMessageResolver.MASKED);
        return ChatClient.builder(new ScriptedChatModel("retrieve"))
                .defaultTools(retrieveTool)
                .defaultAdvisors(ToolCallAdvisor.builder()
                        .toolCallingManager(recording)
                        .build())
                .build();
    }

    private ChatClient plainTextClient(String text) {
        return ChatClient.builder(new ScriptedChatModel(null) {
            @Override
            public Flux<ChatResponse> stream(Prompt prompt) {
                return Flux.just(delta(text), delta("Still no citations."));
            }
        }).build();
    }

    private String perform(ChatClient client, String prompt) throws Exception {
        return perform(client, prompt, true, null);
    }

    private String performWithContext(ChatClient client, String prompt, String context) throws Exception {
        return perform(client, prompt, true, context);
    }

    private String perform(ChatClient client, String prompt, boolean quotaAllowed) throws Exception {
        return perform(client, prompt, quotaAllowed, null);
    }

    @SuppressWarnings("unchecked")
    private String perform(ChatClient client, String prompt, boolean quotaAllowed, String context) throws Exception {
        if (quotaAllowed) {
            when(quotaService.canSpend(userId, CoachTurnSupport.PER_TURN_ESTIMATE_ILM_TOKENS)).thenReturn(true);
            when(quotaService.reserve(userId, CoachTurnSupport.PER_TURN_ESTIMATE_ILM_TOKENS)).thenReturn(reservation);
        }
        ObjectProvider<ChatClient> clientProvider = mock(ObjectProvider.class);
        when(clientProvider.getIfAvailable()).thenReturn(client);
        ObjectProvider<IlmaiChatClientFactory> factoryProvider = mock(ObjectProvider.class);
        when(factoryProvider.getIfAvailable()).thenReturn(null);
        CoachTurnSupport turnSupport = new CoachTurnSupport(
                quotaService,
                mock(IlmTokenCostCalculator.class),
                factoryProvider,
                chatMemorySummarizer,
                userFactExtractor,
                mock(ApplicationEventPublisher.class));
        CoachStreamService service = new CoachStreamService(
                clientProvider,
                chatSessionService,
                turnSupport,
                messageService,
                new UiMessageStreamEmitter(),
                Runnable::run,
                mock(ChatTranscriptService.class));
        AgentController controller = new AgentController(service);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();

        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(currentUser, null));

        String contextJson = context == null ? ""
                : ",\"context\":" + new ObjectMapper().writeValueAsString(context);
        MvcResult asyncStart = mvc.perform(post("/agent/chat/{sessionId}", sessionId)
                        .principal(new TestingAuthenticationToken(currentUser, null))
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content("{\"prompt\":\"" + prompt + "\"" + contextJson + ",\"channel\":\"WEB\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        return mvc.perform(asyncDispatch(asyncStart))
                .andExpect(status().isOk())
                .andExpect(header().string("x-vercel-ai-ui-message-stream", "v1"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private static class ScriptedChatModel implements ChatModel {

        private final String toolName;

        ScriptedChatModel(String toolName) {
            this.toolName = toolName;
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            return stream(prompt).blockLast();
        }

        @Override
        public ChatOptions getDefaultOptions() {
            return ToolCallingChatOptions.builder().build();
        }

        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            return Flux.defer(() -> {
                if (prompt.getInstructions().getLast() instanceof ToolResponseMessage) {
                    return Flux.just(
                            delta("Photosynthesis converts "),
                            delta("light into energy "),
                            delta("[#" + "mat" + ":t2]."));
                }
                AssistantMessage toolCallMessage = AssistantMessage.builder()
                        .content("")
                        .toolCalls(List.of(new AssistantMessage.ToolCall(
                                "call_1", "function", toolName, "{\"query\":\"photosynthesis\"}")))
                        .build();
                return Flux.just(ChatResponse.builder()
                        .generations(List.of(new Generation(toolCallMessage)))
                        .build());
            });
        }

        static ChatResponse delta(String text) {
            return ChatResponse.builder()
                    .generations(List.of(new Generation(new AssistantMessage(text))))
                    .build();
        }
    }
}
