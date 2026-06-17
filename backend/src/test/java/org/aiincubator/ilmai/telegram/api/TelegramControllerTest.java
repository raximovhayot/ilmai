package org.aiincubator.ilmai.telegram.api;

import org.aiincubator.ilmai.common.CurrentUser;
import org.aiincubator.ilmai.auth.security.CurrentUserAuthentication;
import org.aiincubator.ilmai.common.api.GlobalExceptionHandler;
import org.aiincubator.ilmai.common.config.LocalizationConfig;
import org.aiincubator.ilmai.common.i18n.MessageService;
import org.aiincubator.ilmai.telegram.payload.TelegramLinkResponse;
import org.aiincubator.ilmai.telegram.service.TelegramException;
import org.aiincubator.ilmai.telegram.service.TelegramService;
import org.aiincubator.ilmai.telegram.service.TelegramUpdateHandler;
import org.aiincubator.ilmai.telegram.service.TelegramUpdateParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TelegramControllerTest {

    private MockMvc mvc;
    private TelegramService telegramService;
    private TelegramUpdateHandler telegramUpdateHandler;

    @BeforeEach
    void setUp() {
        LocalizationConfig localizationConfig = new LocalizationConfig();
        MessageService messageService = new MessageService(localizationConfig.messageSource());
        TelegramExceptionHandler handler = new TelegramExceptionHandler(messageService);

        telegramService = mock(TelegramService.class);
        telegramUpdateHandler = mock(TelegramUpdateHandler.class);
        TelegramController controller = new TelegramController(
                telegramService, telegramUpdateHandler, new TelegramUpdateParser());

        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(handler)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();

        SecurityContextHolder.getContext().setAuthentication(currentUserAuth(UUID.randomUUID()));
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void get_whenLinked_returnsLinkResponse() throws Exception {
        TelegramLinkResponse response = new TelegramLinkResponse();
        response.setBotUsername("IlmAiBot");
        response.setTelegramUsername("user_tg");

        given(telegramService.getLink(any(CurrentUser.class))).willReturn(response);

        mvc.perform(MockMvcRequestBuilders.get("/telegram"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.botUsername").value("IlmAiBot"))
                .andExpect(jsonPath("$.data.telegramUsername").value("user_tg"));

        verify(telegramService).getLink(any(CurrentUser.class));
    }

    @Test
    void get_whenNotLinked_returnsNotFound() throws Exception {
        given(telegramService.getLink(any(CurrentUser.class)))
                .willThrow(new TelegramException(TelegramException.Reason.TELEGRAM_NOT_LINKED));

        mvc.perform(MockMvcRequestBuilders.get("/telegram"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errors[0].code").value("TELEGRAM_NOT_LINKED"));
    }

    @Test
    void createLinkCode_returnsLinkCode() throws Exception {
        TelegramLinkResponse response = new TelegramLinkResponse();
        response.setBotUsername("IlmAiBot");
        response.setLinkCode("XYZ12345");

        given(telegramService.createLinkCode(any(CurrentUser.class))).willReturn(response);

        mvc.perform(MockMvcRequestBuilders.post("/telegram/link-code"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.botUsername").value("IlmAiBot"))
                .andExpect(jsonPath("$.data.linkCode").value("XYZ12345"));

        verify(telegramService).createLinkCode(any(CurrentUser.class));
    }

    @Test
    void webhook_deserializesLibraryUpdateFromJsonAndDispatchesToHandler() throws Exception {
        String body = """
                {
                  "update_id": 42,
                  "message": {
                    "message_id": 7,
                    "date": 1700000000,
                    "chat": {"id": 12345, "type": "private"},
                    "from": {"id": 999, "is_bot": false, "first_name": "Ali", "username": "ali_tg"},
                    "text": "explain photosynthesis"
                  }
                }
                """;

        mvc.perform(MockMvcRequestBuilders.post("/telegram/webhook/secret-1")
                        .header("X-Telegram-Bot-Api-Secret-Token", "secret-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        ArgumentCaptor<Update> captor = ArgumentCaptor.forClass(Update.class);
        verify(telegramUpdateHandler).handleWebhook(eq("secret-1"), eq("secret-1"), captor.capture());
        Update update = captor.getValue();
        assertThat(update.getUpdateId()).isEqualTo(42);
        assertThat(update.getMessage()).isNotNull();
        assertThat(update.getMessage().getChat().getId()).isEqualTo(12345L);
        assertThat(update.getMessage().getText()).isEqualTo("explain photosynthesis");
        assertThat(update.getMessage().getFrom().getUserName()).isEqualTo("ali_tg");
    }

    @Test
    void unlink_callsUnlinkAndReturnsNoContent() throws Exception {
        mvc.perform(MockMvcRequestBuilders.delete("/telegram"))
                .andExpect(status().isNoContent());

        verify(telegramService).unlink(any(CurrentUser.class));
    }

    private static CurrentUserAuthentication currentUserAuth(UUID userId) {
        Jwt jwt = Jwt.withTokenValue("dummy")
                .header("alg", "HS256")
                .subject(userId.toString())
                .issuedAt(Instant.now().minusSeconds(60))
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        return new CurrentUserAuthentication(new CurrentUser(userId), jwt, List.of());
    }
}
