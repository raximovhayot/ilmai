package org.aiincubator.ilmai.auth.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.aiincubator.ilmai.auth.service.AuthException;
import org.aiincubator.ilmai.auth.service.AuthService;
import org.aiincubator.ilmai.common.config.LocalizationConfig;
import org.aiincubator.ilmai.common.i18n.MessageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.LocaleResolver;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthExceptionHandlerLocalizationTest {

    private MockMvc mvc;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        LocalizationConfig localizationConfig = new LocalizationConfig();
        MessageService messageService = new MessageService(localizationConfig.messageSource());
        LocaleResolver localeResolver = localizationConfig.localeResolver();
        AuthExceptionHandler handler = new AuthExceptionHandler(messageService);

        authService = mock(AuthService.class);
        AuthController controller = new AuthController(authService);

        HandlerInterceptor localeInterceptor = new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object h) {
                LocaleContextHolder.setLocale(localeResolver.resolveLocale(request));
                return true;
            }
        };

        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(handler)
                .addInterceptors(localeInterceptor)
                .build();
    }

    @AfterEach
    void clearLocale() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    void englishMessageIsReturnedByDefault() throws Exception {
        given(authService.loginWithGoogle(anyString()))
                .willThrow(new AuthException(AuthException.Reason.INVALID_GOOGLE_TOKEN));

        mvc.perform(post("/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"x\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_GOOGLE_TOKEN"))
                .andExpect(jsonPath("$.errors[0].message").value("Google ID token is invalid"));
    }

    @Test
    void englishMessageIsReturnedWhenAcceptLanguageIsEn() throws Exception {
        given(authService.loginWithGoogle(anyString()))
                .willThrow(new AuthException(AuthException.Reason.INVALID_GOOGLE_TOKEN));

        mvc.perform(post("/auth/google")
                        .header("Accept-Language", "en")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"x\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_GOOGLE_TOKEN"))
                .andExpect(jsonPath("$.errors[0].message").value("Google ID token is invalid"));
    }

    @Test
    void russianMessageIsReturnedWhenAcceptLanguageIsRu() throws Exception {
        given(authService.loginWithGoogle(anyString()))
                .willThrow(new AuthException(AuthException.Reason.INVALID_GOOGLE_TOKEN));

        mvc.perform(post("/auth/google")
                        .header("Accept-Language", "ru")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"x\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_GOOGLE_TOKEN"))
                .andExpect(jsonPath("$.errors[0].message")
                        .value("Идентификационный токен Google недействителен"));
    }

    @Test
    void uzbekMessageIsReturnedWhenAcceptLanguageIsUz() throws Exception {
        given(authService.loginWithGoogle(anyString()))
                .willThrow(new AuthException(AuthException.Reason.INVALID_GOOGLE_TOKEN));

        mvc.perform(post("/auth/google")
                        .header("Accept-Language", "uz")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"x\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_GOOGLE_TOKEN"))
                .andExpect(jsonPath("$.errors[0].message").value("Google identifikatsiya tokeni yaroqsiz"));
    }

    @Test
    void unsupportedLocaleFallsBackToEnglish() throws Exception {
        given(authService.loginWithGoogle(anyString()))
                .willThrow(new AuthException(AuthException.Reason.INVALID_GOOGLE_TOKEN));

        mvc.perform(post("/auth/google")
                        .header("Accept-Language", "de")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"x\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_GOOGLE_TOKEN"))
                .andExpect(jsonPath("$.errors[0].message").value("Google ID token is invalid"));
    }

    @Test
    void disabledUserIsForbidden_localizedInRussian() throws Exception {
        given(authService.loginWithGoogle(anyString()))
                .willThrow(new AuthException(AuthException.Reason.USER_DISABLED));

        mvc.perform(post("/auth/google")
                        .header("Accept-Language", "ru")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"x\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errors[0].code").value("USER_DISABLED"))
                .andExpect(jsonPath("$.errors[0].message").value("Учётная запись пользователя неактивна"));
    }

    @Test
    void localeNegotiatedFromQualityValuesPicksRussian() throws Exception {
        given(authService.loginWithGoogle(anyString()))
                .willThrow(new AuthException(AuthException.Reason.USER_NOT_FOUND));

        mvc.perform(post("/auth/google")
                        .header("Accept-Language", "fr;q=0.5, ru;q=0.9, en;q=0.1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"x\"}"))
                .andExpect(jsonPath("$.errors[0].message").value("Пользователь не найден"));
    }
}
