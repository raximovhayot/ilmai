package org.aiincubator.ilmai.spaces.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.aiincubator.ilmai.common.CurrentUser;
import org.aiincubator.ilmai.auth.security.CurrentUserAuthentication;
import org.aiincubator.ilmai.common.config.LocalizationConfig;
import org.aiincubator.ilmai.common.i18n.MessageService;
import org.aiincubator.ilmai.spaces.payload.SpaceResponse;
import org.aiincubator.ilmai.spaces.service.SpaceException;
import org.aiincubator.ilmai.spaces.service.SpaceService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SpaceExceptionHandlerLocalizationTest {

    private MockMvc mvc;
    private SpaceService spaceService;
    private UUID userId;

    @BeforeEach
    void setUp() {
        LocalizationConfig localizationConfig = new LocalizationConfig();
        MessageService messageService = new MessageService(localizationConfig.messageSource());
        LocaleResolver localeResolver = localizationConfig.localeResolver();
        SpaceExceptionHandler handler = new SpaceExceptionHandler(messageService);

        spaceService = mock(SpaceService.class);
        SpaceController controller = new SpaceController(spaceService);
        userId = UUID.randomUUID();

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

        SecurityContextHolder.getContext().setAuthentication(currentUserAuth(userId));
    }

    @AfterEach
    void clearLocale() {
        LocaleContextHolder.resetLocaleContext();
        SecurityContextHolder.clearContext();
    }

    @Test
    void renameBlank_returnsBadRequestInEnglish() throws Exception {
        UUID spaceId = UUID.randomUUID();
        given(spaceService.rename(any(CurrentUser.class), any(UUID.class), anyString()))
                .willThrow(new SpaceException(SpaceException.Reason.NAME_BLANK));

        mvc.perform(MockMvcRequestBuilders.patch("/spaces/{spaceId}", spaceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"valid name placeholder\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("NAME_BLANK"))
                .andExpect(jsonPath("$.errors[0].message")
                        .value("Space name must not be blank and must be at most 120 characters"));
    }

    @Test
    void renameBlank_localizedInRussian() throws Exception {
        UUID spaceId = UUID.randomUUID();
        given(spaceService.rename(any(CurrentUser.class), any(UUID.class), anyString()))
                .willThrow(new SpaceException(SpaceException.Reason.NAME_BLANK));

        mvc.perform(MockMvcRequestBuilders.patch("/spaces/{spaceId}", spaceId)
                        .header("Accept-Language", "ru")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"valid name placeholder\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("NAME_BLANK"))
                .andExpect(jsonPath("$.errors[0].message")
                        .value("Название пространства не должно быть пустым и не должно превышать 120 символов"));
    }

    @Test
    void renameBlank_localizedInUzbek() throws Exception {
        UUID spaceId = UUID.randomUUID();
        given(spaceService.rename(any(CurrentUser.class), any(UUID.class), anyString()))
                .willThrow(new SpaceException(SpaceException.Reason.NAME_BLANK));

        mvc.perform(MockMvcRequestBuilders.patch("/spaces/{spaceId}", spaceId)
                        .header("Accept-Language", "uz")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"valid name placeholder\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("NAME_BLANK"))
                .andExpect(jsonPath("$.errors[0].message")
                        .value("Maydon nomi boʻsh boʻlmasligi va 120 belgidan oshmasligi kerak"));
    }

    @Test
    void renameWithUnknownSpace_returnsNotFound() throws Exception {
        UUID spaceId = UUID.randomUUID();
        given(spaceService.rename(any(CurrentUser.class), eq(spaceId), anyString()))
                .willThrow(new SpaceException(SpaceException.Reason.SPACE_NOT_FOUND));

        mvc.perform(MockMvcRequestBuilders.patch("/spaces/{spaceId}", spaceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"valid name placeholder\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errors[0].code").value("SPACE_NOT_FOUND"))
                .andExpect(jsonPath("$.errors[0].message").value("Space not found"));
    }

    @Test
    void list_returnsOkAndDelegatesToService() throws Exception {
        UUID spaceId = UUID.randomUUID();
        given(spaceService.getAll(any(CurrentUser.class)))
                .willReturn(List.of(SpaceResponse.builder()
                        .id(spaceId)
                        .name("Aziza's private space")
                        .build()));

        mvc.perform(MockMvcRequestBuilders.get("/spaces"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(spaceId.toString()))
                .andExpect(jsonPath("$.data[0].name").value("Aziza's private space"));
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
