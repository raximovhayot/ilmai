package org.aiincubator.ilmai.agent.api;

import org.aiincubator.ilmai.agent.ChatChannel;
import org.aiincubator.ilmai.agent.service.ChatSessionService;
import org.aiincubator.ilmai.agent.service.ChatTranscriptService;
import org.aiincubator.ilmai.common.CurrentUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ChatSessionControllerTest {

    private ChatSessionService chatSessionService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        chatSessionService = mock(ChatSessionService.class);
        mvc = MockMvcBuilders.standaloneSetup(
                new ChatSessionController(chatSessionService, mock(ChatTranscriptService.class)))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createReturnsCreatedSession() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        CurrentUser currentUser = new CurrentUser(userId);
        authenticate(currentUser);
        given(chatSessionService.create(any(CurrentUser.class), any()))
                .willReturn(ChatSessionResponse.builder()
                        .id(sessionId)
                        .channel(ChatChannel.WEB)
                        .title("Algebra")
                        .build());

        mvc.perform(post("/agent/sessions")
                        .principal(new TestingAuthenticationToken(currentUser, null))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Algebra\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(sessionId.toString())))
                .andExpect(content().string(containsString("Algebra")));

        verify(chatSessionService).create(any(CurrentUser.class), any());
    }

    @Test
    void listReturnsUsersSessions() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        CurrentUser currentUser = new CurrentUser(userId);
        authenticate(currentUser);
        given(chatSessionService.getAll(any(CurrentUser.class)))
                .willReturn(List.of(ChatSessionResponse.builder()
                        .id(sessionId)
                        .channel(ChatChannel.WEB)
                        .build()));

        mvc.perform(get("/agent/sessions")
                        .principal(new TestingAuthenticationToken(currentUser, null)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(sessionId.toString())));

        verify(chatSessionService).getAll(any(CurrentUser.class));
    }

    private static void authenticate(CurrentUser currentUser) {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(currentUser, null));
    }
}
