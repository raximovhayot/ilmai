package org.aiincubator.ilmai.agent.api;

import org.aiincubator.ilmai.agent.ChatChannel;
import org.aiincubator.ilmai.agent.service.CoachStreamService;
import org.aiincubator.ilmai.common.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;
import uz.uzinfoweb.uimessagestream.spring.ChatClientResponseMapper;
import uz.uzinfoweb.uimessagestream.spring.UiMessageStreamEmitter;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AgentControllerSmokeTest {

    private CoachStreamService coachStreamService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        coachStreamService = mock(CoachStreamService.class);
        AgentController controller = new AgentController(coachStreamService);
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void streamsUiMessageStreamProtocolOverSse() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        CurrentUser currentUser = new CurrentUser(userId);
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(currentUser, null));

        given(coachStreamService.stream(any(CurrentUser.class), eq(sessionId), eq("hello"), isNull(), eq(ChatChannel.WEB)))
                .willAnswer(invocation -> new UiMessageStreamEmitter().from(
                        Flux.just(chunk("hi "), chunk("there")),
                        ChatClientResponseMapper.TEXT_ONLY,
                        Runnable::run));

        MvcResult asyncStart = mvc.perform(post("/agent/chat/{sessionId}", sessionId)
                        .principal(new TestingAuthenticationToken(currentUser, null))
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content("{\"prompt\":\"hello\",\"channel\":\"WEB\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        String body = mvc.perform(asyncDispatch(asyncStart))
                .andExpect(status().isOk())
                .andExpect(header().string("x-vercel-ai-ui-message-stream", "v1"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body).contains("\"type\":\"text-delta\"");
        assertThat(body).contains("\"delta\":\"hi \"");
        assertThat(body).contains("[DONE]");

        SecurityContextHolder.clearContext();
    }

    private static ChatClientResponse chunk(String text) {
        ChatResponse chatResponse = new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
        return new ChatClientResponse(chatResponse, Map.of());
    }
}
