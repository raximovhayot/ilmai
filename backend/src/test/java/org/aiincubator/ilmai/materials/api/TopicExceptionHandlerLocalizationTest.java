package org.aiincubator.ilmai.materials.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.aiincubator.ilmai.common.CurrentUser;
import org.aiincubator.ilmai.auth.security.CurrentUserAuthentication;
import org.aiincubator.ilmai.common.config.LocalizationConfig;
import org.aiincubator.ilmai.common.i18n.MessageService;
import org.aiincubator.ilmai.materials.payload.TopicResponse;
import org.aiincubator.ilmai.materials.service.TopicException;
import org.aiincubator.ilmai.materials.service.TopicService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.LocaleResolver;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TopicExceptionHandlerLocalizationTest {

    private MockMvc mvc;
    private TopicService topicService;

    @BeforeEach
    void setUp() {
        LocalizationConfig localizationConfig = new LocalizationConfig();
        MessageService messageService = new MessageService(localizationConfig.messageSource());
        LocaleResolver localeResolver = localizationConfig.localeResolver();
        TopicExceptionHandler handler = new TopicExceptionHandler(messageService);

        topicService = mock(TopicService.class);
        TopicController controller = new TopicController(topicService);

        HandlerInterceptor localeInterceptor = new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object h) {
                LocaleContextHolder.setLocale(localeResolver.resolveLocale(request));
                return true;
            }
        };

        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(handler)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .addInterceptors(localeInterceptor)
                .build();

        SecurityContextHolder.getContext().setAuthentication(currentUserAuth(UUID.randomUUID()));
    }

    @AfterEach
    void clearLocale() {
        LocaleContextHolder.resetLocaleContext();
        SecurityContextHolder.clearContext();
    }

    @Test
    void createBlank_returnsBadRequestInEnglish() throws Exception {
        given(topicService.create(any(CurrentUser.class), anyString()))
                .willThrow(new TopicException(TopicException.Reason.TOPIC_NAME_BLANK));

        mvc.perform(MockMvcRequestBuilders.post("/topics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"valid placeholder\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("TOPIC_NAME_BLANK"))
                .andExpect(jsonPath("$.errors[0].message")
                        .value("Topic name must not be blank and must be at most 120 characters"));
    }

    @Test
    void createBlank_localizedInRussian() throws Exception {
        given(topicService.create(any(CurrentUser.class), anyString()))
                .willThrow(new TopicException(TopicException.Reason.TOPIC_NAME_BLANK));

        mvc.perform(MockMvcRequestBuilders.post("/topics")
                        .header("Accept-Language", "ru")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"valid placeholder\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].message")
                        .value("Название темы не должно быть пустым и не должно превышать 120 символов"));
    }

    @Test
    void createBlank_localizedInUzbek() throws Exception {
        given(topicService.create(any(CurrentUser.class), anyString()))
                .willThrow(new TopicException(TopicException.Reason.TOPIC_NAME_BLANK));

        mvc.perform(MockMvcRequestBuilders.post("/topics")
                        .header("Accept-Language", "uz")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"valid placeholder\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].message")
                        .value("Mavzu nomi boʻsh boʻlmasligi va 120 belgidan oshmasligi kerak"));
    }

    @Test
    void createTaken_returnsConflictWithInterpolatedName() throws Exception {
        given(topicService.create(any(CurrentUser.class), anyString()))
                .willThrow(new TopicException(TopicException.Reason.TOPIC_NAME_TAKEN, "Cloud"));

        mvc.perform(MockMvcRequestBuilders.post("/topics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cloud\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("TOPIC_NAME_TAKEN"))
                .andExpect(jsonPath("$.errors[0].message")
                        .value("Topic name 'Cloud' is already in use within this space"));
    }

    @Test
    void renameMissingTopic_returnsNotFound() throws Exception {
        UUID topicId = UUID.randomUUID();
        given(topicService.rename(any(CurrentUser.class), any(UUID.class), anyString()))
                .willThrow(new TopicException(TopicException.Reason.TOPIC_NOT_FOUND));

        mvc.perform(MockMvcRequestBuilders.patch("/topics/{topicId}", topicId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"valid placeholder\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errors[0].code").value("TOPIC_NOT_FOUND"))
                .andExpect(jsonPath("$.errors[0].message").value("Topic not found"));
    }

    @Test
    void create_returnsCreatedTopic() throws Exception {
        UUID topicId = UUID.randomUUID();
        UUID spaceId = UUID.randomUUID();
        given(topicService.create(any(CurrentUser.class), anyString()))
                .willReturn(TopicResponse.builder().id(topicId).spaceId(spaceId).name("Cloud").build());

        mvc.perform(MockMvcRequestBuilders.post("/topics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cloud\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(topicId.toString()))
                .andExpect(jsonPath("$.data.spaceId").value(spaceId.toString()))
                .andExpect(jsonPath("$.data.name").value("Cloud"));
    }

    @Test
    void delete_returnsNoContent() throws Exception {
        UUID topicId = UUID.randomUUID();

        mvc.perform(MockMvcRequestBuilders.delete("/topics/{topicId}", topicId))
                .andExpect(status().isNoContent());
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
