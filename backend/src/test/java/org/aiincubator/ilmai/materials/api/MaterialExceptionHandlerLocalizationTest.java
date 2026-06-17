package org.aiincubator.ilmai.materials.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.aiincubator.ilmai.common.CurrentUser;
import org.aiincubator.ilmai.auth.security.CurrentUserAuthentication;
import org.aiincubator.ilmai.common.api.GlobalExceptionHandler;
import org.aiincubator.ilmai.common.config.LocalizationConfig;
import org.aiincubator.ilmai.common.i18n.MessageService;
import org.aiincubator.ilmai.materials.service.MaterialException;
import org.aiincubator.ilmai.materials.service.MaterialService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
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
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MaterialExceptionHandlerLocalizationTest {

    private MockMvc mvc;
    private MaterialService materialService;

    @BeforeEach
    void setUp() {
        LocalizationConfig localizationConfig = new LocalizationConfig();
        MessageService messageService = new MessageService(localizationConfig.messageSource());
        LocaleResolver localeResolver = localizationConfig.localeResolver();
        MaterialExceptionHandler handler = new MaterialExceptionHandler(messageService);

        materialService = mock(MaterialService.class);
        MaterialController controller = new MaterialController(materialService);

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
    void notFound_returnsLocalizedEnglish() throws Exception {
        UUID materialId = UUID.randomUUID();
        given(materialService.get(any(CurrentUser.class), any(UUID.class)))
                .willThrow(new MaterialException(MaterialException.Reason.MATERIAL_NOT_FOUND));

        mvc.perform(MockMvcRequestBuilders.get("/materials/{materialId}", materialId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errors[0].code").value("MATERIAL_NOT_FOUND"))
                .andExpect(jsonPath("$.errors[0].message").value("Material not found"));
    }

    @Test
    void notFound_localizedInRussian() throws Exception {
        UUID materialId = UUID.randomUUID();
        given(materialService.get(any(CurrentUser.class), any(UUID.class)))
                .willThrow(new MaterialException(MaterialException.Reason.MATERIAL_NOT_FOUND));

        mvc.perform(MockMvcRequestBuilders.get("/materials/{materialId}", materialId)
                        .header("Accept-Language", "ru"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errors[0].message").value("Материал не найден"));
    }

    @Test
    void notFound_localizedInUzbek() throws Exception {
        UUID materialId = UUID.randomUUID();
        given(materialService.get(any(CurrentUser.class), any(UUID.class)))
                .willThrow(new MaterialException(MaterialException.Reason.MATERIAL_NOT_FOUND));

        mvc.perform(MockMvcRequestBuilders.get("/materials/{materialId}", materialId)
                        .header("Accept-Language", "uz"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errors[0].message").value("Material topilmadi"));
    }

    @Test
    void unsupportedType_returnsBadRequestWithInterpolatedType() throws Exception {
        given(materialService.get(any(CurrentUser.class), any(UUID.class)))
                .willThrow(new MaterialException(MaterialException.Reason.MATERIAL_UNSUPPORTED_TYPE, "application/pdf"));

        mvc.perform(MockMvcRequestBuilders.get("/materials/{materialId}", UUID.randomUUID()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("MATERIAL_UNSUPPORTED_TYPE"))
                .andExpect(jsonPath("$.errors[0].message")
                        .value("Content type 'application/pdf' is not supported yet"));
    }

    @Test
    void tooLarge_returnsContentTooLarge() throws Exception {
        given(materialService.get(any(CurrentUser.class), any(UUID.class)))
                .willThrow(new MaterialException(MaterialException.Reason.MATERIAL_TOO_LARGE, 26_214_400L));

        mvc.perform(MockMvcRequestBuilders.get("/materials/{materialId}", UUID.randomUUID()))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.errors[0].code").value("MATERIAL_TOO_LARGE"));
    }

    @Test
    void topicNotFound_returnsNotFound() throws Exception {
        given(materialService.get(any(CurrentUser.class), any(UUID.class)))
                .willThrow(new MaterialException(MaterialException.Reason.MATERIAL_TOPIC_NOT_FOUND));

        mvc.perform(MockMvcRequestBuilders.get("/materials/{materialId}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errors[0].code").value("MATERIAL_TOPIC_NOT_FOUND"));
    }

    @Test
    void storageFailed_returnsServiceUnavailable() throws Exception {
        given(materialService.get(any(CurrentUser.class), any(UUID.class)))
                .willThrow(new MaterialException(MaterialException.Reason.MATERIAL_STORAGE_FAILED));

        mvc.perform(MockMvcRequestBuilders.get("/materials/{materialId}", UUID.randomUUID()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.errors[0].code").value("MATERIAL_STORAGE_FAILED"));
    }

    @Test
    void materialException_takesPrecedenceOverGlobalFallback() throws Exception {
        LocalizationConfig localizationConfig = new LocalizationConfig();
        MessageService messageService = new MessageService(localizationConfig.messageSource());
        GlobalExceptionHandler globalHandler = new GlobalExceptionHandler(messageService);
        MaterialExceptionHandler materialHandler = new MaterialExceptionHandler(messageService);

        MockMvc mvcWithBoth = MockMvcBuilders.standaloneSetup(new MaterialController(materialService))
                .setControllerAdvice(globalHandler, materialHandler)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();

        given(materialService.get(any(CurrentUser.class), any(UUID.class)))
                .willThrow(new MaterialException(MaterialException.Reason.MATERIAL_NOT_FOUND));

        mvcWithBoth.perform(MockMvcRequestBuilders.get("/materials/{materialId}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errors[0].code").value("MATERIAL_NOT_FOUND"));
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
